package pl.snowdog.kiosk

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.app.admin.DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
import android.app.admin.SystemUpdatePolicy
import android.content.*
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.provider.Settings
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.widget.Toast
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private lateinit var adminComponentName: ComponentName
    private lateinit var devicePolicyManager: DevicePolicyManager

    companion object {
        const val LOCK_ACTIVITY_KEY = "pl.snowdog.kiosk.MainActivity"
    }

    fun Any.log() {
        Log.d("MEGA", "$this")
    }


    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        adminComponentName = MyDeviceAdminReceiver.getComponentName(this)
        devicePolicyManager = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager


        val isAdmin = isAdmin()
        if (isAdmin) {
            Toast.makeText(applicationContext, R.string.device_owner, Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(applicationContext, R.string.not_device_owner, Toast.LENGTH_SHORT).show()
        }
        btnShowNotification.setOnClickListener {
            showNotification()
        }
        btStartLockTask.setOnClickListener {
            setKioskPolicies(true, isAdmin)
        }
        btnNavigate.setOnClickListener {
            startActivity(Intent(this, SecondActivity::class.java))
        }
        btnStartLocationUpdates.setOnClickListener {
            val permissionGrantState = devicePolicyManager.getPermissionGrantState(adminComponentName, packageName, Manifest.permission.ACCESS_FINE_LOCATION)
            "permissionGrantState: $permissionGrantState".log()
            if (permissionGrantState != 1) {
                devicePolicyManager.setPermissionGrantState(adminComponentName, packageName, Manifest.permission.ACCESS_FINE_LOCATION, PERMISSION_GRANT_STATE_GRANTED)
            } else {

                val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 100F, object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationTextView.text = "Location: ${location.latitude},${location.longitude}"
                    }

                })
            }
        }
        btStopLockTask.setOnClickListener {
            setKioskPolicies(false, isAdmin)
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            intent.putExtra(LOCK_ACTIVITY_KEY, false)
            startActivity(intent)
        }
        btInstallApp.setOnClickListener {
            installApp()
        }
    }

    @SuppressLint("NewApi")
    private fun showNotification() {

        val id = "noty"
        val notificationChannel = NotificationChannel(id, "Test Channel", NotificationManager.IMPORTANCE_HIGH)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(notificationChannel)
        val notification = Notification.Builder(this, id)
                .setContentTitle("Test notification in kiosk mode")
                .setContentText("this verifies that notification do infact work in kiosk mode")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()
        notificationManager.notify(0, notification)
    }

    private fun isAdmin() = devicePolicyManager.isDeviceOwnerApp(packageName)

    private fun setKioskPolicies(enable: Boolean, isAdmin: Boolean) {
        if (isAdmin) {
            setRestrictions(enable)
            enableStayOnWhilePluggedIn(enable)
            setUpdatePolicy(enable)
            setAsHomeApp(enable)
            setKeyGuardEnabled(enable)
        }
        setLockTask(enable, isAdmin)
        setImmersiveMode(enable)
    }


    // region restrictions
    private fun setRestrictions(disallow: Boolean) {
        setUserRestriction(UserManager.DISALLOW_SAFE_BOOT, disallow)

        setUserRestriction(UserManager.DISALLOW_FACTORY_RESET, disallow)
        setUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS, disallow)
        setUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES, disallow)
        setUserRestriction(UserManager.DISALLOW_ADD_USER, disallow)
        setUserRestriction(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, disallow)
        setUserRestriction(UserManager.DISALLOW_ADJUST_VOLUME, disallow)
        setUserRestriction(UserManager.DISALLOW_CONFIG_WIFI, false)
        setUserRestriction(UserManager.DISALLOW_AIRPLANE_MODE, disallow)
        setUserRestriction(UserManager.DISALLOW_SYSTEM_ERROR_DIALOGS, disallow)
        setUserRestriction(UserManager.DISALLOW_UNINSTALL_APPS, disallow)
        devicePolicyManager.setStatusBarDisabled(adminComponentName, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

            devicePolicyManager.setLockTaskFeatures(
                    adminComponentName,
                    DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS or DevicePolicyManager.LOCK_TASK_FEATURE_HOME)
        }

    }

    private fun setUserRestriction(restriction: String, disallow: Boolean) = if (disallow) {
        devicePolicyManager.addUserRestriction(adminComponentName, restriction)
    } else {
        devicePolicyManager.clearUserRestriction(adminComponentName, restriction)
    }
    // endregion

    private fun enableStayOnWhilePluggedIn(active: Boolean) = if (active) {
        devicePolicyManager.setGlobalSetting(adminComponentName,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                (BatteryManager.BATTERY_PLUGGED_AC
                        or BatteryManager.BATTERY_PLUGGED_USB
                        or BatteryManager.BATTERY_PLUGGED_WIRELESS).toString())
    } else {
        devicePolicyManager.setGlobalSetting(adminComponentName, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "0")
    }

    private fun setLockTask(start: Boolean, isAdmin: Boolean) {
        if (isAdmin) {
            devicePolicyManager.setLockTaskPackages(
                    adminComponentName, if (start) arrayOf(packageName, "com.mrugas.smallapp") else arrayOf())
        }
        if (start) {
            startLockTask()
        } else {
            stopLockTask()
        }
    }

    private fun setUpdatePolicy(enable: Boolean) {
        if (enable) {
            devicePolicyManager.setSystemUpdatePolicy(adminComponentName,
                    SystemUpdatePolicy.createWindowedInstallPolicy(60, 120))
        } else {
            devicePolicyManager.setSystemUpdatePolicy(adminComponentName, null)
        }
    }

    private fun setAsHomeApp(enable: Boolean) {
        if (enable) {
            val intentFilter = IntentFilter(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
            devicePolicyManager.addPersistentPreferredActivity(
                    adminComponentName, intentFilter, ComponentName(packageName, MainActivity::class.java.name))
        } else {
            devicePolicyManager.clearPackagePersistentPreferredActivities(
                    adminComponentName, packageName)
        }
    }

    private fun setKeyGuardEnabled(enable: Boolean) {
        devicePolicyManager.setKeyguardDisabled(adminComponentName, !enable)
    }


    private fun setImmersiveMode(enable: Boolean) {
        if (enable) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val insetsController = window.insetsController
                insetsController?.hide(WindowInsets.Type.systemBars() or
                        WindowInsets.Type.systemGestures() or
                        WindowInsets.Type.mandatorySystemGestures()
                )
            } else {
                val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                window.decorView.systemUiVisibility = flags
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {

                val insetsController = window.insetsController
                insetsController?.show(WindowInsets.Type.systemBars() or
                        WindowInsets.Type.systemGestures() or
                        WindowInsets.Type.mandatorySystemGestures()
                )
            } else {
                val flags = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
                window.decorView.systemUiVisibility = flags
            }
        }
    }

    private fun createIntentSender(context: Context?, sessionId: Int, packageName: String?): IntentSender? {
        val intent = Intent("INSTALL_COMPLETE")
        if (packageName != null) {
            intent.putExtra("PACKAGE_NAME", packageName)
        }
        val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                intent,
                0)
        return pendingIntent.intentSender
    }

    private fun installApp() {
        if (!isAdmin()) {
            Toast.makeText(this, "Not a Device Owner", Toast.LENGTH_LONG).show()
            return
        }
        val raw = resources.openRawResource(R.raw.other_app)
        val packageInstaller: PackageInstaller = packageManager.packageInstaller
        val params = SessionParams(
                SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName("com.mrugas.smallapp")
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
        val out = session.openWrite("SmallApp", 0, -1)
        val buffer = ByteArray(65536)
        var c: Int
        while (raw.read(buffer).also { c = it } != -1) {
            out.write(buffer, 0, c)
        }
        session.fsync(out)
        out.close()
        createIntentSender(this, sessionId, packageName)?.let { intentSender ->
            session.commit(intentSender)
        }
        session.close()
    }
}
