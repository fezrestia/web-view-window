@file:Suppress("PrivatePropertyName", "ConstantConditionIf")

package com.fezrestia.android.webviewwindow.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference

import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.webviewwindow.R
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.App
import com.fezrestia.android.webviewwindow.receiver.WebViewWindowReceiver

class SettingActivity : AppCompatActivity() {
    private val TAG = "SettingActivity"

    private val REQUEST_CODE_MANAGE_PERMISSIONS = 100

    /**
     * Fragment for Settings.
     */
    class SettingFragment : PreferenceFragmentCompat() {
        private val TAG = "SettingFragment"

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.setting, rootKey)

            // Setup static preference options.

        }

        override fun onStart() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStart()")
            super.onStart()

            val onChangeListenerImpl = OnChangeListenerImpl()

            // Setup dynamic preference options.

            // En/Disable.
            val enDisPref: SwitchPreference = findPreference(Constants.SP_KEY_WWW_ENABLE_DISABLE)!!
            enDisPref.isChecked = App.isEnabled
            enDisPref.onPreferenceChangeListener = onChangeListenerImpl

            // DEBUG force crash.
            val forceCrash: SwitchPreference = findPreference(Constants.SP_KEY_DEBUG_FORCE_CRASH)!!
            forceCrash.onPreferenceChangeListener = onChangeListenerImpl
        }

        override fun onStop() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStop()")
            super.onStop()

            // NOP.

        }

        private inner class OnChangeListenerImpl : Preference.OnPreferenceChangeListener {
            override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
                when (preference.key) {
                    Constants.SP_KEY_WWW_ENABLE_DISABLE -> {
                        val context = requireContext()

                        val toggleIntent = Intent(
                                context.applicationContext,
                                WebViewWindowReceiver::class.java)
                        toggleIntent.action = Constants.INTENT_ACTION_TOGGLE_ENABLE_DISABLE

                        context.sendBroadcast(toggleIntent)
                    }

                    Constants.SP_KEY_BASE_LOAD_URL -> {
                        val url = newValue as String?
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "BaseLoadURL = $url")

                        val summary = if (url != null) {
                            if (url.isEmpty()) {
                                "DEFAULT:\n${Constants.DEFAULT_BASE_LOAD_URL}"
                            } else {
                                "URL:\n$url"
                            }
                        } else {
                            "URL is reset to DEFAULT."
                        }
                        preference.summary = summary
                    }

                    Constants.SP_KEY_CUSTOM_USER_AGENT_FOR_MOBILE,
                    Constants.SP_KEY_CUSTOM_USER_AGENT_FOR_DESKTOP -> {
                        val ua = newValue as String?
                        if (Log.IS_DEBUG) Log.logDebug(TAG, "CustomUserAgent = $ua")

                        val summary = if (ua != null && ua.isNotEmpty()) {
                            "User-Agent\n$ua"
                        } else {
                            "Use DEFAULT"
                        }

                        preference.summary = summary
                    }

                    // DEBUG.

                    Constants.SP_KEY_DEBUG_FORCE_CRASH -> {
                        throw RuntimeException("Force Crash Triggered !")
                    }

                    else -> {
                        throw RuntimeException("UnSupported Preference : key=${preference.key}")
                    }
                }

                return true
            }
        }
    }

    //// MAIN ACTIVITY

    private lateinit var settingFragment: SettingFragment

    override fun onCreate(bundle: Bundle?) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onCreate()")
        super.onCreate(null)

        setContentView(R.layout.setting_activity)

        settingFragment = SettingFragment()

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.preference_fragment_container, settingFragment)
                .commit()
    }

    private fun checkPermissions() {
        if (isFinishing) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "App is in finishing sequence.")
            return
        }
        if (checkMandatoryPermissions()) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Return immediately for permission.")
            return
        }
    }

    override fun onStart() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStart()")
        super.onStart()

        checkPermissions()
    }

    override fun onResume() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")
        super.onResume()

        checkPermissions()
    }

    override fun onPause() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")
        super.onPause()
        // NOP.
    }

    override fun onStop() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onStop()")
        super.onStop()
        // NOP.
    }

    override fun onDestroy() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onDestroy()")
        super.onDestroy()
        // NOP.
    }

    //// RUNTIME PERMISSION RELATED

    private val isRuntimePermissionRequired: Boolean
        get() = Build.VERSION_CODES.M <= Build.VERSION.SDK_INT

    private val isSystemAlertWindowPermissionGranted: Boolean
        get() = Settings.canDrawOverlays(this)

    private val REQUIRED_PERMISSIONS =
        arrayOf(
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )

    private fun isPermissionGranted(permission: String): Boolean =
            checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Check permission.

     * @return immediateReturnRequired
     */
    private fun checkMandatoryPermissions(): Boolean {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "checkMandatoryPermissions()")

        if (isRuntimePermissionRequired) {
            if (!isSystemAlertWindowPermissionGranted) {
                // Start permission setting.
                val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"))

                val startForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    REQUIRED_PERMISSIONS.forEach { permission ->
                        if (!isPermissionGranted(permission)) {
                            Log.logError(TAG, "Permission: $permission is NOT granted yet.")
                            finish()
                        }
                    }
                }
                startForResult.launch(intent)

                return true
            }

            val permissions = mutableListOf<String>()

            REQUIRED_PERMISSIONS.forEach { permission ->
                if(!isPermissionGranted(permission)) {
                    permissions.add(permission)
                }
            }

            // Check background permission will be granted after foreground.
            if (permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION)
                    || permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION)) {
                permissions.remove(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }

            return if (permissions.isNotEmpty()) {
                ActivityCompat.requestPermissions(
                        this,
                        permissions.toTypedArray(),
                        REQUEST_CODE_MANAGE_PERMISSIONS)
                true
            } else {
                false
            }
        } else {
            return false
        }
    }
}
