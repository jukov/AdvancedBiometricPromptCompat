package dev.skomlach.biometric.compat.utils

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.WindowManager
import androidx.annotation.RestrictTo
import androidx.core.util.ObjectsCompat
import androidx.fragment.app.FragmentActivity
import dev.skomlach.biometric.compat.utils.logging.BiometricLoggerImpl.e
import java.util.*

@RestrictTo(RestrictTo.Scope.LIBRARY)
object ActiveWindow {
    private var clazz: Class<*>? = null
    private var windowManager: Any? = null
    private var windowManagerClazz: Class<*>? = null
    init {
        try {
            clazz = Class.forName("android.view.ViewRootImpl")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                windowManagerClazz = Class.forName("android.view.WindowManagerGlobal")
                windowManagerClazz?.getMethod("getInstance")?.invoke(null).also { windowManager = it }
            } else {
                windowManagerClazz = Class.forName("android.view.WindowManagerImpl")
                windowManagerClazz?.getMethod("getDefault")?.invoke(null).also { windowManager = it }
            }
        } catch (e: Throwable) {
            e(e)
        }
    }
    fun getActiveView(activity: FragmentActivity): View {
        val list = viewRoots
        var topView: View? = null
        for (i in list.indices) {
            val viewParent = list[i]
            try {
                val view = clazz?.getMethod("getView")?.invoke(viewParent) as View
                val type = (view.layoutParams as WindowManager.LayoutParams).type
                if (type >= WindowManager.LayoutParams.FIRST_SYSTEM_WINDOW) {
                    continue
                }
                if (!viewBelongActivity(view, activity)) continue
                if (topView == null) {
                    topView = view
                } else {
                    val topViewType = (topView.layoutParams as WindowManager.LayoutParams).type
                    if (type > topViewType) {
                        topView = view
                    } else if (view.hasWindowFocus() && !topView.hasWindowFocus()) {
                        topView = view
                    }
                }
            } catch (e: Throwable) {
                e(e, "ActiveWindow.getActiveView")
            }
        }
        if (topView != null) {
            e("ActiveWindow.getActiveView-$topView")
            return topView
        }
        throw IllegalStateException("Unable to find Active Window to attach")
    }

    private fun viewBelongActivity(view: View?, activity: Activity): Boolean {
        if (view == null) return false
        var context: Context? = extractActivity(view.context)
        if (context == null) context = view.context
        if (ObjectsCompat.equals(activity, context)) {
            return true
        } else if (view is ViewGroup) {
            val vg = view
            for (i in 0 until vg.childCount) {
                if (viewBelongActivity(vg.getChildAt(i), activity)) return true
            }
        }
        return false
    }

    private fun extractActivity(c: Context): Activity? {
        var context = c
        while (true) {
            context = when (context) {
                is Application -> {
                    return null
                }
                is Activity -> {
                    return context
                }
                is ContextWrapper -> {
                    val baseContext = context.baseContext
                    // Prevent Stack Overflow.
                    if (baseContext === context) {
                        return null
                    }
                    baseContext
                }
                else -> {
                    return null
                }
            }
        }
    }

    // Filter out inactive view roots
    private val viewRoots: List<ViewParent>
        get() {
            val viewRoots: MutableList<ViewParent> = ArrayList()
            try {
                val rootsField = windowManagerClazz?.getDeclaredField("mRoots")
                val isAccessibleRootsField = rootsField?.isAccessible
                try {
                    if (isAccessibleRootsField == false) rootsField?.isAccessible = true
                    val stoppedField = clazz?.getDeclaredField("mStopped")
                    val isAccessible = stoppedField?.isAccessible
                    try {
                        if (isAccessible == false) stoppedField?.isAccessible = true
                        val lst = rootsField?.get(windowManager)
                        val viewParents: MutableList<ViewParent> = ArrayList()
                        try {
                            viewParents.addAll((lst as List<ViewParent>))
                        } catch (ignore: ClassCastException) {
                            val parents = lst as Array<ViewParent>
                            viewParents.addAll(listOf(*parents))
                        }
                        // Filter out inactive view roots
                        for (viewParent in viewParents) {
                            val stopped = stoppedField?.get(viewParent) as Boolean
                            if (!stopped) {
                                viewRoots.add(viewParent)
                            }
                        }
                    } finally {
                        if (isAccessible == false) stoppedField.isAccessible = false
                    }
                } finally {
                    if (isAccessibleRootsField == false) rootsField.isAccessible = false
                }
            } catch (e: Exception) {
                e(e, "ActiveWindow")
            }
            return viewRoots
        }

}