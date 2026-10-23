package me.gorani.launchingview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Returns the default `onOpenUri` handler: an `ACTION_VIEW` intent on the current context.
 */
@Composable
public fun rememberDefaultUriOpener(): (Uri) -> Unit {
  val context = LocalContext.current
  return remember(context) { { uri -> context.openUriExternally(uri) } }
}

internal fun Context.openUriExternally(uri: Uri) {
  val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  try {
    startActivity(intent)
  } catch (error: ActivityNotFoundException) {
    Log.w("LaunchingView", "No activity can open $uri", error)
  }
}

internal fun Context.applicationLabel(): String =
  applicationInfo.loadLabel(packageManager).toString()
