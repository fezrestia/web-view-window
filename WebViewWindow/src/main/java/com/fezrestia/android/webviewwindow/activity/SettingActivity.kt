@file:Suppress("PrivatePropertyName", "ConstantConditionIf")

package com.fezrestia.android.webviewwindow.activity

import android.Manifest
import android.annotation.TargetApi
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference

import com.fezrestia.android.webviewwindow.Constants
import com.fezrestia.android.webviewwindow.R
import com.fezrestia.android.util.Log
import com.fezrestia.android.webviewwindow.App
import com.fezrestia.android.webviewwindow.service.OverlayViewService

class SettingActivity : AppCompatActivity() {
    private val TAG = "SettingActivity"

    private val REQUEST_CODE_MANAGE_OVERLAY_PERMISSION = 100
    private val REQUEST_CODE_MANAGE_PERMISSIONS = 200

    /**
     * Fragment for Settings.
     */
    class SettingFragment : PreferenceFragmentCompat() {
        private val TAG = "SettingFragment"

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.setting, rootKey)

            val onChangeListenerImpl = OnChangeListenerImpl()

            // Setup static preference options.

            // En/Disable.
            val enDisPref: SwitchPreference = findPreference(Constants.SP_KEY_WWW_ENABLE_DISABLE)!!
            enDisPref.isChecked = App.isEnabled
            enDisPref.onPreferenceChangeListener = onChangeListenerImpl

        }

        override fun onStart() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStart()")
            super.onStart()

            // Setup dynamic preference options.

        }

        override fun onStop() {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "onStop()")
            super.onStop()

            // NOP.

        }

        private inner class OnChangeListenerImpl : Preference.OnPreferenceChangeListener {
            override fun onPreferenceChange(preference: Preference?, newValue: Any?): Boolean {
                when (preference?.key) {
                    Constants.SP_KEY_WWW_ENABLE_DISABLE -> {
                        val isChecked: Boolean = newValue as Boolean
                        val context = requireContext()

                        if (isChecked) {
                            val service = Intent(context, OverlayViewService::class.java)
                            val component = context.startForegroundService(service)

                            if (Log.IS_DEBUG) {
                                if (component != null) {
                                    Log.logDebug(TAG, "startService() : Component = $component")
                                } else {
                                    Log.logDebug(TAG, "startService() : Component = NULL")
                                }
                            }
                        } else {
                            val service = Intent(context, OverlayViewService::class.java)
                            val isSuccess = context.stopService(service)

                            if (Log.IS_DEBUG) {
                                Log.logDebug(TAG, "stopService() : isSuccess = $isSuccess")
                            }
                        }
                    }

                    Constants.SP_KEY_BASE_LOAD_URL -> {
                        val url: String? = newValue as String
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

                    else -> {
                        throw RuntimeException("UnSupported Preference : key=${preference?.key}")
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

    override fun onResume() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onResume()")
        super.onResume()

        // Mandatory permission check.
        if (isFinishing) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "App is in finishing sequence.")
            return
        }
        if (checkMandatoryPermissions()) {
            if (Log.IS_DEBUG) Log.logDebug(TAG, "Return immediately for permission.")
            return
        }

    }

    override fun onPause() {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onPause()")
        super.onPause()
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
        @TargetApi(Build.VERSION_CODES.M)
        get() = Settings.canDrawOverlays(this)

    private val isWriteStoragePermissionGranted: Boolean
        @TargetApi(Build.VERSION_CODES.M)
        get() = (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED)

    private val isAccessCoarseLocationPermissionGranted: Boolean
        @TargetApi(Build.VERSION_CODES.M)
        get() = (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)

    private val isAccessFineLocationPermissionGranted: Boolean
        @TargetApi(Build.VERSION_CODES.M)
        get() = (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED)

    /**
     * Check permission.

     * @return immediateReturnRequired
     */
    @TargetApi(Build.VERSION_CODES.M)
    private fun checkMandatoryPermissions(): Boolean {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "checkMandatoryPermissions()")

        if (isRuntimePermissionRequired) {
            if (!isSystemAlertWindowPermissionGranted) {
                // Start permission setting.
                val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName"))
                startActivityForResult(intent, REQUEST_CODE_MANAGE_OVERLAY_PERMISSION)

                return true
            }

            val permissions = mutableListOf<String>()

            if (!isWriteStoragePermissionGranted) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            if (!isAccessCoarseLocationPermissionGranted) {
                permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (!isAccessFineLocationPermissionGranted) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onActivityResult()")
        super.onActivityResult(requestCode, resultCode, intent)

        if (requestCode == REQUEST_CODE_MANAGE_OVERLAY_PERMISSION) {
            if (!isSystemAlertWindowPermissionGranted) {
                Log.logError(TAG, "Overlay permission is not granted yet.")
                finish()
            }
        }
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray) {
        if (Log.IS_DEBUG) Log.logDebug(TAG, "onRequestPermissionsResult()")

        if (requestCode == REQUEST_CODE_MANAGE_PERMISSIONS) {
            if (!isWriteStoragePermissionGranted) {
                Log.logError(TAG,"Write storage permission is not granted yet.")
                finish()
            }
            if (!isAccessCoarseLocationPermissionGranted) {
                Log.logError(TAG,"Access coarse location permission is not granted yet.")
                finish()
            }
            if (!isAccessFineLocationPermissionGranted) {
                Log.logError(TAG,"Access fine location permission is not granted yet.")
                finish()
            }
        }
    }
}
