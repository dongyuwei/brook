// Copyright (c) 2016-present Cloud <cloud@txthinking.com>
//
// This program is free software; you can redistribute it and/or
// modify it under version 3 of the GNU General Public License
// as published by the Free Software Foundation.
//
// This program is distributed in the hope that it will be useful, but
// WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
// General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.

// Package mobile exposes the brook tunnel plus a TUN-to-SOCKS5 bridge
// for Android, consumed through gomobile bindings.
package mobile

import (
	"fmt"
	"net"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/txthinking/brook"
	"github.com/xjasonlyu/tun2socks/v2/core"
	"github.com/xjasonlyu/tun2socks/v2/core/device"
	"github.com/xjasonlyu/tun2socks/v2/core/device/fdbased"
	proxysocks5 "github.com/xjasonlyu/tun2socks/v2/proxy/socks5"
	"github.com/xjasonlyu/tun2socks/v2/tunnel"
	"golang.org/x/sys/unix"
	"gvisor.dev/gvisor/pkg/tcpip/stack"
)

var (
	mu sync.Mutex

	blk         *brook.BrookLink
	netstack    *stack.Stack
	tunDevice   device.Device
)

const (
	tcpTimeout = 0  // seconds; 0 means no timeout, streams stay up as long as peers keep them
	udpTimeout = 60 // seconds; matches the netstack UDP session timeout
)

// TestLink verifies that a brook link can be parsed and is of a
// tunnelable kind. It does not touch the network.
func TestLink(link string) error {
	_, err := parseLink(link)
	return err
}

func parseLink(link string) (*brook.BrookLink, error) {
	link = strings.TrimSpace(link)
	if link == "" {
		return nil, fmt.Errorf("empty brook link")
	}
	blk, err := brook.NewBrookLink(link)
	if err != nil {
		return nil, err
	}
	switch blk.Kind {
	case "server", "wsserver", "wssserver", "quicserver":
		return blk, nil
	}
	return nil, fmt.Errorf("unsupported link kind %q, want server/wsserver/wssserver/quicserver", blk.Kind)
}

// Start brings the VPN data path up: a local SOCKS5 server that tunnels
// through the brook link, and a TUN-to-SOCKS5 netstack reading from tunFd.
//
// Start takes ownership of tunFd: the descriptor is closed on every path,
// including error returns, so the caller must not close or reuse it.
// mtu must match the value given to VpnService.Builder.setMtu.
func Start(tunFd int, link string, mtu int) error {
	mu.Lock()
	defer mu.Unlock()
	if blk != nil {
		return fmt.Errorf("tunnel already running")
	}
	if mtu <= 0 {
		mtu = 1500
	}

	b, err := parseLink(link)
	if err != nil {
		unix.Close(tunFd)
		return err
	}

	// Pick a free loopback port for the local SOCKS5 server. The socks5
	// library binds TCP and UDP to one explicit port, so an ephemeral
	// ":0" bind cannot be used (TCP and UDP would get different ports).
	port, err := freePort()
	if err != nil {
		unix.Close(tunFd)
		return err
	}
	addr := net.JoinHostPort("127.0.0.1", strconv.Itoa(port))
	if err := b.PrepareSocks5Server(addr, "127.0.0.1", tcpTimeout, udpTimeout); err != nil {
		unix.Close(tunFd)
		return err
	}
	errCh := make(chan error, 1)
	go func() {
		errCh <- b.ListenAndServe()
	}()
	select {
	case err := <-errCh:
		unix.Close(tunFd)
		return fmt.Errorf("socks5 server exited: %w", err)
	case <-time.After(300 * time.Millisecond):
	}

	p, err := proxysocks5.New(addr, "", "")
	if err != nil {
		unix.Close(tunFd)
		b.Shutdown()
		return err
	}
	t := tunnel.T()
	t.SetUDPTimeout(udpTimeout * time.Second)
	t.SetProxy(p)

	// Assemble the netstack directly instead of going through the
	// package-level engine, whose Start/Stop call log.Fatalf (os.Exit)
	// on failure — unacceptable inside an Android app process.
	d, err := fdbased.Open(strconv.Itoa(tunFd), uint32(mtu), 0)
	if err != nil {
		unix.Close(tunFd)
		b.Shutdown()
		return err
	}
	s, err := core.CreateStack(&core.Config{
		LinkEndpoint:     d,
		TransportHandler: t,
	})
	if err != nil {
		d.Close()
		b.Shutdown()
		return err
	}

	blk = b
	netstack = s
	tunDevice = d
	return nil
}

// Stop tears the data path down and closes the TUN descriptor owned by
// Start. Returns an error if the tunnel is not running.
func Stop() error {
	mu.Lock()
	defer mu.Unlock()
	if blk == nil {
		return fmt.Errorf("tunnel not running")
	}
	b := blk
	s := netstack
	d := tunDevice
	blk = nil
	netstack = nil
	tunDevice = nil

	s.Close()
	s.Wait()
	d.Close()
	b.Shutdown()
	return nil
}

// Running reports whether the tunnel is up.
func Running() bool {
	mu.Lock()
	defer mu.Unlock()
	return blk != nil
}

func freePort() (int, error) {
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return 0, err
	}
	defer l.Close()
	return l.Addr().(*net.TCPAddr).Port, nil
}
