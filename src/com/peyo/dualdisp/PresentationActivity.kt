package com.peyo.dualdisp

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.content.DialogInterface
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.SparseArray
import android.view.Display
import android.view.View
import android.view.ViewGroup
import android.widget.*


class PresentationActivity : Activity(), CompoundButton.OnCheckedChangeListener {
    private val TAG = "PresentationActivity"
    private var mDisplayManager: DisplayManager? = null
    private var mDisplayListAdapter: DisplayListAdapter? = null
    private var mShowAllDisplaysCheckbox: CheckBox? = null
    private var mListView: ListView? = null

    // List of all currently visible presentations indexed by display id.
    private val mActivePresentations = SparseArray<DemoPresentation?>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mDisplayManager = getSystemService(DISPLAY_SERVICE) as DisplayManager

        setContentView(R.layout.presentation_activity)

        // Set up checkbox to toggle between showing all displays or only presentation displays.
        mShowAllDisplaysCheckbox = findViewById<View>(R.id.show_all_displays) as CheckBox
        mShowAllDisplaysCheckbox!!.setOnCheckedChangeListener(this)

        mDisplayListAdapter = DisplayListAdapter(this)
        mListView = findViewById<View>(R.id.display_list) as ListView
        mListView!!.adapter = mDisplayListAdapter
        mListView!!.setOnItemClickListener { _, view, _, _ ->
            view.findViewById<CheckBox>(R.id.checkbox_presentation).performClick()
        }
    }

    override fun onResume() {
        super.onResume()
        mDisplayListAdapter!!.updateContents()
        mDisplayManager!!.registerDisplayListener(mDisplayListener, null)
    }

    override fun onPause() {
        super.onPause()
        mDisplayManager!!.unregisterDisplayListener(mDisplayListener)

        // Dismiss all of our presentations but remember their contents.
        Log.d(TAG, "Activity is being paused.  Dismissing all active presentation.")
        for (i in 0 until mActivePresentations.size()) {
            val presentation = mActivePresentations.valueAt(i)
            presentation?.dismiss()
        }
        mActivePresentations.clear()
    }

    /**
     * Shows a [Presentation] on the specified display.
     */
    private fun showPresentation(display: Display?) {
        val displayId = display!!.displayId
        if (mActivePresentations[displayId] != null) {
            return
        }
        val presentation = DemoPresentation(this, display)
        presentation.show()
        presentation.setOnDismissListener(mOnDismissListener)
        mActivePresentations.put(displayId, presentation)
    }

    /**
     * Hides a [Presentation] on the specified display.
     */
    private fun hidePresentation(display: Display) {
        val displayId = display.displayId
        val presentation = mActivePresentations[displayId] ?: return
        Log.d(TAG, "Dismissing presentation on display #$displayId.")
        presentation.dismiss()
        mActivePresentations.delete(displayId)
    }

    /**
     * Called when the show all displays checkbox is toggled or when
     * an item in the list of displays is checked or unchecked.
     */
    override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
        if (buttonView === mShowAllDisplaysCheckbox) {
            // Show all displays checkbox was toggled.
            mDisplayListAdapter!!.updateContents()
        } else {
            // Display item checkbox was toggled.
            val display = buttonView.tag as Display
            if (isChecked) {
                showPresentation(display)
            } else {
                hidePresentation(display)
            }
            mDisplayListAdapter!!.updateContents()
        }
    }

    /**
     * Listens for displays to be added, changed or removed.
     * We use it to update the list and show a new [Presentation] when a
     * display is connected.
     *
     * Note that we don't bother dismissing the [Presentation] when a
     * display is removed, although we could.  The presentation API takes care
     * of doing that automatically for us.
     */
    private val mDisplayListener: DisplayListener = object : DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.d(TAG, "Display #$displayId added.")
            mDisplayListAdapter!!.updateContents()
        }

        override fun onDisplayChanged(displayId: Int) {
            Log.d(TAG, "Display #$displayId changed.")
            mDisplayListAdapter!!.updateContents()
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d(TAG, "Display #$displayId removed.")
            mDisplayListAdapter!!.updateContents()
        }
    }

    /**
     * Listens for when presentations are dismissed.
     */
    private val mOnDismissListener =
        DialogInterface.OnDismissListener { dialog ->
            val presentation = dialog as DemoPresentation
            val displayId = presentation.display.displayId
            Log.d(TAG, "Presentation on display #$displayId was dismissed.")
            mActivePresentations.delete(displayId)
            mDisplayListAdapter!!.notifyDataSetChanged()
        }

    /**
     * List adapter.
     * Shows information about all displays.
     */
    private inner class DisplayListAdapter(val mContext: Context) :
        ArrayAdapter<Display?>(mContext, R.layout.presentation_list_item) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val v: View
            if (convertView == null) {
                v = (mContext as Activity).layoutInflater.inflate(
                    R.layout.presentation_list_item, null
                )
            } else {
                v = convertView
            }
            val display = getItem(position)
            val displayId = display!!.displayId
            val cb = v.findViewById<View>(R.id.checkbox_presentation) as CheckBox
            cb.tag = display
            cb.setOnCheckedChangeListener(this@PresentationActivity)
            val tv = v.findViewById<View>(R.id.display_id) as TextView
            tv.text = v.context.resources.getString(
                R.string.presentation_display_id_text, displayId
            )
            return v
        }

        /**
         * Update the contents of the display list adapter to show
         * information about all current displays.
         */
        fun updateContents() {
            clear()
            val displayCategory = displayCategory
            val displays = mDisplayManager!!.getDisplays(displayCategory)
            addAll(*displays)
            Log.d(TAG, "There are currently " + displays.size + " displays connected.")
            for (display in displays) {
                Log.d(TAG, "  $display")
            }
        }

        private val displayCategory: String?
            get() = if (mShowAllDisplaysCheckbox!!.isChecked) null else DisplayManager.DISPLAY_CATEGORY_PRESENTATION

    }

    /**
     * The presentation to show on the secondary display.
     *
     * Note that the presentation display may have different metrics from the display on which
     * the main activity is showing so we must be careful to use the presentation's
     * own [Context] whenever we load resources.
     */
    private inner class DemoPresentation(
        context: Context?, display: Display?,
    ) : Presentation(context, display) {
        private lateinit var mVideoView: VideoView

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.presentation_content)

            mVideoView = this.findViewById(R.id.video1)
            val URL = "https://commondatastorage.googleapis.com/android-tv/Sample%20videos/Zeitgeist/Zeitgeist%202010_%20Year%20in%20Review.mp4"
            mVideoView.setVideoURI(Uri.parse(URL))
        }

        override fun show() {
            super.show()
            mVideoView.start()
        }
    }
}
