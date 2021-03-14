package com.peyo.dualdisp

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.content.DialogInterface
import android.graphics.Point
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.DisplayManager.DisplayListener
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.os.Parcelable.Creator
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
    private var mNextImageNumber = 0

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
    private fun showPresentation(display: Display?, contents: DemoPresentationContents) {
        val displayId = display!!.displayId
        if (mActivePresentations[displayId] != null) {
            return
        }
        Log.d(
            TAG, "Showing presentation photo #" + contents.photo
                    + " on display #" + displayId + "."
        )
        val presentation = DemoPresentation(this, display, contents)
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

    private val nextPhoto: Int
        get() {
            val photo = mNextImageNumber
            mNextImageNumber = (mNextImageNumber + 1) % PHOTOS.size
            return photo
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
                val contents = DemoPresentationContents(
                    nextPhoto
                )
                showPresentation(display, contents)
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
            val presentation = mActivePresentations[displayId]
            var contents = presentation?.mContents
            val cb = v.findViewById<View>(R.id.checkbox_presentation) as CheckBox
            cb.tag = display
            cb.setOnCheckedChangeListener(this@PresentationActivity)
            cb.isChecked = contents != null
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
        val mContents: DemoPresentationContents
    ) : Presentation(context, display) {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // Get the resources for the context of the presentation.
            // Notice that we are getting the resources from the context of the presentation.
            val r = context.resources

            // Inflate the layout.
            setContentView(R.layout.presentation_content)
            val display = display
            val displayId = display.displayId
            val photo = mContents.photo

            // Show a caption to describe what's going on.
            val text = findViewById<View>(R.id.text) as TextView
            text.text = r.getString(
                R.string.presentation_photo_text,
                photo, displayId, display.name
            )

            // Show a n image for visual interest.
            val image = findViewById<View>(R.id.image) as ImageView
            image.setImageDrawable(r.getDrawable(PHOTOS[photo]))
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.RECTANGLE
            drawable.gradientType = GradientDrawable.RADIAL_GRADIENT

            // Set the background to a random gradient.
            val p = Point()
            getDisplay().getSize(p)
            drawable.gradientRadius = (Math.max(p.x, p.y) / 2).toFloat()
            drawable.colors = mContents.colors
            findViewById<View>(android.R.id.content).background = drawable
        }
    }

    /**
     * Information about the content we want to show in the presentation.
     */
    private class DemoPresentationContents : Parcelable {
        val photo: Int
        val colors: IntArray

        constructor(photo: Int) {
            this.photo = photo
            colors = intArrayOf(
                (Math.random() * Int.MAX_VALUE).toInt() or -0x1000000,
                (Math.random() * Int.MAX_VALUE).toInt() or -0x1000000
            )
        }

        private constructor(`in`: Parcel) {
            photo = `in`.readInt()
            colors = intArrayOf(`in`.readInt(), `in`.readInt())
        }

        override fun describeContents(): Int {
            return 0
        }

        override fun writeToParcel(dest: Parcel, flags: Int) {
            dest.writeInt(photo)
            dest.writeInt(colors[0])
            dest.writeInt(colors[1])
        }

        companion object {
            @JvmField val CREATOR: Creator<DemoPresentationContents?> =
                object : Creator<DemoPresentationContents?> {
                    override fun createFromParcel(`in`: Parcel): DemoPresentationContents? {
                        return DemoPresentationContents(`in`)
                    }

                    override fun newArray(size: Int): Array<DemoPresentationContents?> {
                        return arrayOfNulls(size)
                    }
                }
        }
    }

    companion object {
        // The content that we want to show on the presentation.
        private val PHOTOS = intArrayOf(
            R.drawable.frantic,
            R.drawable.photo1, R.drawable.photo2, R.drawable.photo3,
            R.drawable.photo4, R.drawable.photo5, R.drawable.photo6,
            R.drawable.sample_4
        )
    }
}
