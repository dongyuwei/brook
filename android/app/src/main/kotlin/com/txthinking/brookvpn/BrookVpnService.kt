package com.txthinking.brookvpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.VpnService
import android.os.Build
import android.system.Os
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import mobile.Mobile

/**
 * Creates the TUN interface for whitelisted apps only and hands the
 * descriptor to the Go side (brook tunnel + TUN-to-SOCKS5 bridge).
 *
 * Routing model: only packages added via [Builder.addAllowedApplication]
 * are routed into the VPN; everything else (and this app itself) keeps
 * using the underlying network directly.
 */
class BrookVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.txthinking.brookvpn.START"
        const val ACTION_STOP = "com.txthinking.brookvpn.STOP"
        const val ACTION_STATE = "com.txthinking.brookvpn.STATE"
        const val EXTRA_RUNNING = "running"
        const val EXTRA_ERROR = "error"

        private const val CHANNEL_ID = "brook-vpn"
        private const val NOTIFICATION_ID = 1
        private const val MTU = 1500

        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, BrookVpnService::class.java).setAction(ACTION_START),
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, BrookVpnService::class.java).setAction(ACTION_STOP),
            )
        }

        fun broadcastState(context: Context, running: Boolean, error: String? = null) {
            context.sendBroadcast(
                Intent(ACTION_STATE)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_RUNNING, running)
                    .putExtra(EXTRA_ERROR, error),
            )
        }
    }

    // Only touched on the vpn thread; null once torn down.
    private var tunFd: Int? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdown()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForegroundCompat()
                if (running) {
                    broadcastState(this, true)
                    return START_STICKY
                }
                Thread { runVpn() }.start()
            }
        }
        return START_STICKY
    }

    private fun runVpn() {
        val link = Prefs.link(this)
        val whitelist = Prefs.whitelist(this)
        if (link.isBlank()) {
            fail("No brook link configured")
            return
        }
        if (whitelist.isEmpty()) {
            fail("Whitelist is empty: select at least one app")
            return
        }

        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .setMtu(MTU)
            .addAddress("172.19.0.1", 30)
            .addAddress("fd00::1", 126)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)
            // Public resolvers: app DNS queries travel through the tunnel
            // and are resolved remotely by the brook server.
            .addDnsServer("223.5.5.5")
            .addDnsServer("8.8.8.8")
        for (pkg in whitelist) {
            if (pkg == packageName) continue // never route our own traffic
            try {
                builder.addAllowedApplication(pkg)
            } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
                // App was uninstalled; skip it.
            }
        }

        val pfd = try {
            builder.establish()
        } catch (e: Exception) {
            fail("Establish VPN failed: ${e.message}")
            return
        }
        if (pfd == null) {
            fail("Establish VPN failed: VPN permission revoked?")
            return
        }

        // Hand the descriptor over to Go; it owns and closes it from here on.
        val fd = pfd.detachFd()
        tunFd = fd
        try {
            Mobile.start(fd.toLong(), link, MTU.toLong())
        } catch (e: Throwable) {
            tunFd = null
            // Go closes the fd on error paths itself.
            fail("Start tunnel failed: ${e.message}")
            return
        }

        running = true
        broadcastState(this, true)
    }

    private fun fail(error: String) {
        running = false
        broadcastState(this, false, error)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun shutdown() {
        if (running) {
            running = false
            try {
                Mobile.stop()
            } catch (_: Throwable) {
                // Already down; nothing to do.
            }
            tunFd = null
        }
        broadcastState(this, false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (running) {
            running = false
            try {
                Mobile.stop()
            } catch (_: Throwable) {
            }
        }
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun startForegroundCompat() {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this, NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
