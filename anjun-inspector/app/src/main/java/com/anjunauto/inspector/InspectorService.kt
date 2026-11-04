package com.anjunauto.inspector
import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
class InspectorService : AccessibilityService() {
 override fun onAccessibilityEvent(event: AccessibilityEvent?) {
  val eventPackage = event?.packageName?.toString() ?: return
  if (eventPackage == packageName || !eventPackage.contains("anjun", ignoreCase=true)) return
  val root = rootInActiveWindow ?: return
  if (root.packageName?.toString() != eventPackage) return
  val counts = mutableMapOf<String,Int>()
  fun walk(node: AccessibilityNodeInfo?, depth: Int) {
   if (node == null || depth > 12) return
   val cls = node.className?.toString()?.substringAfterLast('.') ?: "Unknown"
   counts[cls] = (counts[cls] ?: 0) + 1
   for (i in 0 until node.childCount) walk(node.getChild(i), depth+1)
  }
  walk(root,0)
  val summary = "Event: ${event.eventType}; elements: ${counts.entries.joinToString { "${it.key}=${it.value}" }}"
  val prefs = getSharedPreferences("inspector", MODE_PRIVATE)
  val old = prefs.getString("report", "") ?: ""
  prefs.edit().putString("report", (old + "\n" + summary).takeLast(20000)).apply()
 }
 override fun onInterrupt() {}
}