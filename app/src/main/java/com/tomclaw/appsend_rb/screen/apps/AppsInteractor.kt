package com.tomclaw.appsend_rb.screen.apps

import com.tomclaw.appsend_rb.dto.AppEntity
import com.tomclaw.appsend_rb.util.SchedulersFactory
import io.reactivex.Observable
import io.reactivex.Single
import java.io.File
import java.util.ArrayList
import java.util.Locale

interface AppsInteractor {

    fun loadApps(systemApps: Boolean, runnableOnly: Boolean, sortOrder: Int): Observable<List<AppEntity>>

}

class AppsInteractorImpl(
        private val packageManager: PackageManagerWrapper,
        private val schedulers: SchedulersFactory
) : AppsInteractor {

    private val locale = Locale.getDefault()

    override fun loadApps(
            systemApps: Boolean,
            runnableOnly: Boolean,
            sortOrder: Int
    ): Observable<List<AppEntity>> {
        return Single
                .create<List<AppEntity>> { emitter ->
                    val entities = loadEntities(systemApps, runnableOnly, sortOrder)
                    emitter.onSuccess(entities)
                }
                .toObservable()
                .subscribeOn(schedulers.io())
    }

    private fun loadEntities(
            systemApps: Boolean,
            runnableOnly: Boolean,
            sortOrder: Int
    ): List<AppEntity> {
        val entities = ArrayList<AppEntity>()
        val packages = packageManager.getInstalledApplications(GET_META_DATA)
        for (info in packages) {
            try {
                val packageInfo = packageManager.getPackageInfo(info.packageName, GET_PERMISSIONS)
                val file = File(info.publicSourceDir)
                if (file.exists()) {
                    val entity = AppEntity(
                            label = packageManager.getApplicationLabel(info),
                            packageName = info.packageName,
                            versionName = packageInfo.versionName,
                            versionCode = packageInfo.versionCode,
                            path = file.path,
                            size = file.length(),
                            firstInstallTime = packageInfo.firstInstallTime,
                            lastUpdateTime = packageInfo.lastUpdateTime
                    )
                    val isUserApp = info.flags and FLAG_SYSTEM != FLAG_SYSTEM &&
                            info.flags and FLAG_UPDATED_SYSTEM_APP != FLAG_UPDATED_SYSTEM_APP
                    if (isUserApp || systemApps) {
                        val launchIntent = packageManager.getLaunchIntentForPackage(info.packageName)
                        if (launchIntent != null || !runnableOnly) {
                            entities += entity
                        }
                    }
                }
            } catch (ignored: Throwable) {
                // Bad package.
            }
        }
        when (sortOrder) {
            NAME_ASCENDING -> entities.sortWith { lhs: AppEntity, rhs: AppEntity -> lhs.label.toUpperCase(locale).compareTo(rhs.label.toUpperCase(locale)) }
            NAME_DESCENDING -> entities.sortWith { lhs: AppEntity, rhs: AppEntity -> rhs.label.toUpperCase(locale).compareTo(lhs.label.toUpperCase(locale)) }
            APP_SIZE -> entities.sortWith { lhs: AppEntity, rhs: AppEntity -> rhs.size.compareTo(lhs.size) }
            INSTALL_TIME -> entities.sortWith { lhs: AppEntity, rhs: AppEntity -> rhs.firstInstallTime.compareTo(lhs.firstInstallTime) }
            UPDATE_TIME -> entities.sortWith { lhs: AppEntity, rhs: AppEntity -> rhs.lastUpdateTime.compareTo(lhs.lastUpdateTime) }
        }

        return entities
    }

}

const val NAME_ASCENDING = 1
const val NAME_DESCENDING = 2
const val APP_SIZE = 3
const val INSTALL_TIME = 4
const val UPDATE_TIME = 5
