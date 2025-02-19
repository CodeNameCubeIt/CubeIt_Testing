package cz.cubeit.cubeit

import android.Manifest
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.solver.widgets.Rectangle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.Exclude
import kotlinx.android.synthetic.main.popup_dialog_recyclerview.view.*
import kotlinx.android.synthetic.main.row_5_characters.view.*
import kotlinx.android.synthetic.main.row_5_icons.view.*
import kotlinx.android.synthetic.main.row_5_icons_text.view.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.*
import java.lang.Exception
import java.util.*
import kotlin.math.*
import kotlin.random.Random.Default.nextInt

object SystemFlow {
    var factionChange: Boolean = false
    var gameActivityTraces: MutableList<GameActivity> = mutableListOf()
    var currentGameActivity: GameActivity? = null
        get() {
            return if (gameActivityTraces.last().isDestroyed) null else gameActivityTraces.last()
        }

    open class GameFragment(private val layoutId: Int, private val viewGroupId: Int) : Fragment() {
        var inflaterView: View? = null

        private fun garbageCollector(){
            val parent = inflaterView?.findViewById<ViewGroup>(viewGroupId)
            if(inflaterView != null && parent != null){
                for(i in 0..parent.childCount){
                    if(parent.getChildAt(i) is ImageView){
                        Log.d("GameFragment_recycler", "recycled view of index: $i")
                        (parent.getChildAt(i) as? ImageView)?.setImageResource(0)
                    }
                }
                System.gc()
            }
        }

        override fun onDestroy() {
            super.onDestroy()
            garbageCollector()
        }

        override fun onLowMemory() {
            super.onLowMemory()
            garbageCollector()
        }

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            inflaterView = inflater.inflate(layoutId, container, false)

            return inflaterView
        }
    }

    open class GameActivity(
            private val contentLayoutId: Int,
            val activityType: ActivityType,
            private val hasMenu: Boolean,
            private val menuUpColor: Int? = null,
            private val isViewPager: Boolean = false,
            private val hasSwipeDown: Boolean = true,
            private val hasSwipeMenu: Boolean = true
    ) : AppCompatActivity(contentLayoutId) {

        val dm = DisplayMetrics()
        var frameLayoutMenuBar: FrameLayout? = null
        var imageViewSwipeDown: ImageView? = null
        var imageViewMenuUp: ImageView? = null
        var menuFragment: Fragment_Menu_Bar? = null
        lateinit var parentViewGroup: ViewGroup
        lateinit var propertiesBar: GamePropertiesBar

        private fun hideSystemUI() {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }

        fun visualizeRewardWith(startingPoint: Coordinates, reward: Reward?, existingPropertiesBar: GamePropertiesBar? = null): ValueAnimator? {
            return visualizeReward(this, startingPoint, reward, existingPropertiesBar)
        }

        fun clearFocus() {
            val imm = getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
            var view = currentFocus
            if (view == null) {
                view = View(this)
            }
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }

        override fun onWindowFocusChanged(hasFocus: Boolean) {
            super.onWindowFocusChanged(hasFocus)
            if (hasFocus) hideSystemUI()
        }

        private fun showMenuBar() {
            if (hasMenu && frameLayoutMenuBar != null) {
                ValueAnimator.ofFloat(frameLayoutMenuBar?.y
                        ?: 0f, (dm.heightPixels * 0.83).toFloat()).apply {
                    duration = 600
                    addUpdateListener {
                        frameLayoutMenuBar?.y = it.animatedValue as Float
                    }
                    start()
                }
            }
        }

        private fun solidMenuBar(): Boolean {
            //Log.d("solidMenuBar", ((frameLayoutMenuBar?.y?.toInt() ?: 0) - dm.heightPixels - (frameLayoutMenuBar?.height ?: 0)).toString())
            val expectedY = dm.heightPixels - (frameLayoutMenuBar?.height ?: 0)
            return (frameLayoutMenuBar?.y?.toInt() ?: 0) in (expectedY - 5)..(expectedY + 5)
        }

        fun hideMenuBar() {
            if (hasMenu && frameLayoutMenuBar != null) {
                ValueAnimator.ofFloat(frameLayoutMenuBar?.y
                        ?: 0f, dm.heightPixels.toFloat()).apply {
                    duration = 600
                    addUpdateListener {
                        frameLayoutMenuBar?.y = it.animatedValue as Float
                    }
                    start()
                }
            }
        }

        private fun initMenuBar() {
            if (hasMenu && frameLayoutMenuBar != null) {
                frameLayoutMenuBar?.post {
                    menuFragment = Fragment_Menu_Bar.newInstance(frameLayoutMenuBar?.id
                            ?: 0, imageViewSwipeDown?.id ?: 0, imageViewMenuUp?.id ?: 0)

                    supportFragmentManager.beginTransaction()
                            .replace(parentViewGroup.findViewWithTag<FrameLayout>("frameLayoutMenuBar$activityType").id, menuFragment!!, "menuBarFragment$activityType").commitAllowingStateLoss()

                    Handler().postDelayed({
                        menuFragment?.setUpSecondAction(View.OnClickListener { if (propertiesBar.isShown) propertiesBar.hide() else propertiesBar.show() })
                    }, 500)
                }
            }
        }

        fun bringControlsToFront() {
            frameLayoutMenuBar?.bringToFront()
            imageViewSwipeDown?.bringToFront()
            imageViewMenuUp?.bringToFront()
        }

        fun hasMenu(): Boolean {
            return hasMenu && frameLayoutMenuBar != null
        }

        var eventType = 0
        var initialTouchY = 0f
        var initialTouchX = 0f
        var originalYMenu = frameLayoutMenuBar?.y ?: dm.heightPixels.toFloat()
        var originalY = 0f

        var menuAnimator = ValueAnimator()
        var iconAnimator = ValueAnimator()

        val lastRecognizedPointer = Coordinates(0f, 0f)
        private var validMenuEvent = false
        private var validSwipeDownEvent = false
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            //val parent = window.decorView.rootView.findViewById<ViewGroup>(android.R.id.content)
            val menuViewRect = Rect()
            val propertyBarViewRect = Rect()
            //val recyclerViewRects = mutableListOf<Rect>()
            frameLayoutMenuBar?.getGlobalVisibleRect(menuViewRect)
            propertiesBar.frameLayoutBar.getGlobalVisibleRect(propertyBarViewRect)

            //Don't interrupt RecyclerViews and ListViews!!
            /*for(i in 0..parent.childCount){
                *//*if(parent.getChildAt(i) is FrameLayout){
                    Log.d("FrameLayoutChild", ((parent.getChildAt(i) as? FrameLayout) as? ViewGroup)?.childCount.toString())
                    Log.d("FrameLayoutChild", ((parent.getChildAt(i) as? FrameLayout) as? ViewGroup)?.findViewById<ViewGroup>(android.R.id.content)?.childCount.toString())
                    for(j in 0..(((parent.getChildAt(i) as? FrameLayout)?.parent as? ViewGroup)?.findViewById<ViewGroup>(android.R.id.content)?.childCount ?: 0)){
                        val view = ((parent.getChildAt(i) as? FrameLayout)?.parent as? ViewGroup)?.findViewById<ViewGroup>(android.R.id.content)?.getChildAt(j)
                        Log.d("FrameLayoutChild", view.toString())
                        Log.d("FrameLayoutChild_focus", (parent.getChildAt(i) as? FrameLayout)?.focusedChild.toString())
                        if((view is RecyclerView || view is ListView) || (parent.getChildAt(i) as? FrameLayout)?.focusedChild is ListView || (parent.getChildAt(i) as? FrameLayout)?.focusedChild is RecyclerView){
                            recyclerViewRects.add(Rect())
                            view?.getGlobalVisibleRect(recyclerViewRects.last())
                            Log.d("dispatchTouchEvent", "adding recyclerview or listview rectangle to the list (from framelayout)")
                        }
                    }
                }*//*
                if(parent.getChildAt(i) is RecyclerView || parent.getChildAt(i) is ListView){
                    recyclerViewRects.add(Rect())
                    parent.getChildAt(i)?.getGlobalVisibleRect(recyclerViewRects.last())
                    Log.d("dispatchTouchEvent", "adding recyclerview or listview rectangle to the list")
                }
            }
            for(i in recyclerViewRects){
                if(i.contains(ev.rawX.toInt(), ev.rawY.toInt())){
                    Log.d("dispatchTouchEvent", "event is on top of a recyclerview or listview")
                    return super.dispatchTouchEvent(ev)
                }
            }
            */
            if (ev.action == MotionEvent.ACTION_DOWN) {
                lastRecognizedPointer.apply(ev.x, ev.y)
            }

            /*Log.d("condition 0", (!menuViewRect.contains(ev.rawX.toInt(), ev.rawY.toInt())).toString())
            Log.d("condition 1", (!menuViewRect.contains(lastRecognizedPointer.x.toInt(), lastRecognizedPointer.y.toInt())).toString())
            Log.d("condition 2", (frameLayoutMenuBar?.y ?: 0f <= (dm.heightPixels * 0.83).toFloat()).toString())
            Log.d("condition 3", (ev.action == MotionEvent.ACTION_UP).toString())
            Log.d("condition 4", (solidMenuBar()).toString())
            Log.d("condition 5", (!propertyBarViewRect.contains(ev.rawX.toInt(), ev.rawY.toInt())).toString())*/
            if (!menuViewRect.contains(ev.rawX.toInt(), ev.rawY.toInt()) && !menuViewRect.contains(lastRecognizedPointer.x.toInt(), lastRecognizedPointer.y.toInt()) && frameLayoutMenuBar?.y ?: 0f <= (dm.heightPixels * 0.83).toFloat() && ev.action == MotionEvent.ACTION_UP && solidMenuBar() && !propertyBarViewRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                hideMenuBar()
            }

            if((this as? ActivityAdventure)?.overViewOpened == true){
                return super.dispatchTouchEvent(ev)
            }

            //swipe down to icon_exit + menu controlling
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    originalYMenu = frameLayoutMenuBar?.y ?: dm.heightPixels.toFloat()
                    //get the touch location
                    initialTouchY = ev.rawY
                    initialTouchX = ev.rawX
                    originalY = imageViewSwipeDown?.y ?: ((-dm.heightPixels * 0.175).toFloat())

                    eventType = when {
                        ev.rawY <= dm.heightPixels * 0.18 && ev.rawX.between((dm.widthPixels * 0.25).toFloat(), (dm.widthPixels * 0.75).toFloat()) -> {
                            if (iconAnimator.isRunning) iconAnimator.pause()
                            imageViewSwipeDown?.bringToFront()
                            if (hasSwipeDown) 1 else 0
                        }
                        ev.rawY >= dm.heightPixels * 0.7 -> {
                            if (menuAnimator.isRunning) menuAnimator.pause()
                            frameLayoutMenuBar?.bringToFront()
                            when {
                                hasSwipeMenu && (hasMenu || (frameLayoutMenuBar?.y ?: 9999f) < dm.heightPixels * 0.9) -> {
                                    2
                                }
                                hasSwipeMenu && ev.rawY <= dm.heightPixels * 0.9 -> {
                                    2
                                }
                                else -> 0
                            }
                        }
                        else -> {
                            0
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!(eventType == 0 || isViewPager)) {
                        validSwipeDownEvent = false
                        validMenuEvent = false

                        if ((originalY + (ev.rawY - initialTouchY).toInt()) <= (dm.heightPixels * 0.5)) {
                            iconAnimator = ValueAnimator.ofFloat(imageViewSwipeDown?.y
                                    ?: 0f, -(dm.heightPixels * 0.18).toFloat()).apply {
                                duration = 600
                                addUpdateListener {
                                    imageViewSwipeDown?.y = it.animatedValue as Float
                                }
                                start()
                            }
                        } else {
                            val intent = Intent(this, ActivityHome::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(intent)
                        }

                        if (frameLayoutMenuBar?.y ?: 0f >= (dm.heightPixels * 0.9)) {
                            menuAnimator = ValueAnimator.ofFloat(frameLayoutMenuBar?.y
                                    ?: 0f, (dm.heightPixels).toFloat()).apply {
                                duration = 600
                                addUpdateListener {
                                    frameLayoutMenuBar?.y = (it.animatedValue as Float)
                                }
                                start()
                            }
                        } else {
                            menuAnimator = ValueAnimator.ofFloat(frameLayoutMenuBar?.y
                                    ?: 0f, (dm.heightPixels - (frameLayoutMenuBar?.height
                                    ?: 0)).toFloat()).apply {
                                duration = 600
                                addUpdateListener {
                                    frameLayoutMenuBar?.y = (it.animatedValue as Float)
                                }
                                start()
                            }
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if(abs(ev.rawY - initialTouchY) > 10f && abs(ev.rawY - initialTouchY) > abs(ev.rawX - initialTouchX)){
                        when(eventType){
                            1 -> validSwipeDownEvent = true
                            2 -> validMenuEvent = true
                        }
                    }

                    if (!(eventType == 0 || isViewPager)) {
                        when (eventType) {
                            1 -> {
                                if(validSwipeDownEvent){
                                    imageViewSwipeDown?.apply {
                                        y = (originalY + (ev.rawY - initialTouchY)) / 3
                                        alpha = (((originalY + (ev.rawY - initialTouchY).toInt()) / (dm.heightPixels * 1.175 / 100) / 100) * 3).toFloat()
                                        rotation = (0.9 * (originalY + (initialTouchY - ev.rawY).toInt() / ((dm.heightPixels / 2) / 100))).toFloat()
                                        //Log.d("systemflow_color", (2.55 * abs(min((originalY + (ev.rawY - initialTouchY)).toInt(), (dm.heightPixels / 10 * 5)) / ((dm.heightPixels / 10 * 5) / 100) - 100)).toString())
                                        //drawable.setColorFilter(Color.rgb(255, 255, min((2.55 * abs((originalY + (ev.rawY - initialTouchY)).toInt() / ((dm.heightPixels / 10 * 5) / 100) - 100)).toInt(), 255)), PorterDuff.Mode.MULTIPLY)
                                        if ((originalY + (ev.rawY - initialTouchY).toInt()) > (dm.heightPixels * 0.5)) {
                                            drawable.setColorFilter(resources.getColor(R.color.colorSecondary), PorterDuff.Mode.SRC_ATOP)
                                        } else drawable.clearColorFilter()
                                        requestLayout()
                                    }
                                }
                            }
                            2 -> {
                                if(validMenuEvent){
                                    if (menuAnimator.isRunning) {
                                        menuAnimator.cancel()
                                    }
                                    frameLayoutMenuBar?.y = max((originalYMenu + ((initialTouchY - ev.rawY) * (-1))), (dm.heightPixels * 0.825).toFloat())
                                }
                            }

                        }
                    }
                }
            }

            return super.dispatchTouchEvent(ev) && ev.action == MotionEvent.ACTION_UP
        }

        override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                respondOnMediaButton(this)
            }
            return super.onKeyDown(keyCode, event)
        }

        /**
         * ImageViews' recycler
         */
        override fun onDestroy() {
            super.onDestroy()
            val parent = window.decorView.rootView.findViewById<ViewGroup>(android.R.id.content)
            for(i in 0..parent.childCount){
                if(parent.getChildAt(i) is ImageView){
                    Log.d("GameActivity_recycler", "recycled view of index: $i")
                    (parent.getChildAt(i) as? ImageView)?.setImageResource(0)
                }
            }
            System.gc()
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            gameActivityTraces.add(this)
            hideSystemUI()
            setContentView(contentLayoutId)
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.defaultDisplay.getRealMetrics(dm)
            parentViewGroup = this.window.decorView.rootView.findViewById(android.R.id.content)

            propertiesBar = GamePropertiesBar(this)

            window.decorView.setOnSystemUiVisibilityChangeListener { visibility ->
                if (visibility and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) {
                    Handler().postDelayed({ hideSystemUI() }, 1000)
                }
            }
            parentViewGroup.post {
                clearFocus()
            }

            if (hasMenu) {
                frameLayoutMenuBar = FrameLayout(this)
                imageViewMenuUp = ImageView(this)

                parentViewGroup.post {
                    frameLayoutMenuBar?.apply {
                        layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
                        layoutParams.height = ((dm.heightPixels * 0.175).toInt())
                        layoutParams.width = dm.widthPixels
                        y = (dm.heightPixels).toFloat()
                        elevation = 10f
                        tag = "frameLayoutMenuBar$activityType"
                        id = View.generateViewId()                  //generate new ID, since adding fragment requires IDs
                    }

                    imageViewMenuUp?.apply {
                        layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
                        layoutParams.height = ((dm.widthPixels * 0.04).toInt())
                        layoutParams.width = ((dm.widthPixels * 0.07).toInt())
                        x = (dm.widthPixels - (dm.widthPixels * 0.07) - 4f).toFloat()
                        y = dm.heightPixels - (dm.widthPixels * 0.04).toFloat()
                        elevation = 9f
                        tag = "imageViewMenuUp$activityType"
                        id = View.generateViewId()
                        setBackgroundResource(R.drawable.arrow_up)
                        background?.setColorFilter(resources.getColor(menuUpColor
                                ?: R.color.loginColor), PorterDuff.Mode.SRC_ATOP)

                        setOnClickListener {
                            showMenuBar()
                        }
                    }
                    parentViewGroup.apply {
                        addView(imageViewMenuUp)
                        addView(frameLayoutMenuBar)
                        invalidate()
                    }
                    initMenuBar()
                }
            }
            if (hasSwipeDown) {
                parentViewGroup.post {
                    imageViewSwipeDown = ImageView(this)

                    imageViewSwipeDown?.apply {
                        layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
                        layoutParams.height = ((dm.heightPixels * 0.175).toInt())
                        layoutParams.width = ((dm.heightPixels * 0.175).toInt())
                        pivotX = (dm.heightPixels * 0.0875).toFloat()
                        pivotY = (dm.heightPixels * 0.0875).toFloat()
                        x = (dm.widthPixels * 0.5 - dm.heightPixels * 0.0875).toFloat()
                        y = -(dm.heightPixels * 0.175).toFloat()
                        setImageResource(R.drawable.home_button)
                        setBackgroundResource(R.drawable.circle_white)
                        elevation = 10f
                        tag = "imageViewSwipeDown$activityType"
                        id = View.generateViewId()
                    }

                    parentViewGroup.apply {
                        addView(imageViewSwipeDown)
                        invalidate()
                    }
                    Handler().postDelayed({
                        imageViewSwipeDown?.background?.setColorFilter(resources.getColor(R.color.loginColor), PorterDuff.Mode.SRC_ATOP)
                    }, 200)
                }
            }
        }
    }

    fun showSocials(activity: GameActivity): FrameLayout {
        return attachNewFragment(
                activity,
                Fragment_Socials(),
                activity.dm.heightPixels,
                activity.dm.heightPixels,
                Coordinates(((activity.dm.widthPixels * 0.5) - (activity.dm.heightPixels * 0.5)).toFloat(), 0f),
                "frameLayoutSocials"
        )
    }

    fun attachNewFragment(activity: GameActivity, fragment: Fragment, width: Int, height: Int, coordinates: Coordinates = Coordinates(0f, 0f), uniqueName: String? = null): FrameLayout{
        val parent: ViewGroup = activity.window.decorView.findViewById(android.R.id.content)
        val frameLayout = FrameLayout(activity)

        if(uniqueName != null){
            parent.removeView(parent.findViewWithTag<FrameLayout>(uniqueName))
        }

        frameLayout.apply {
            layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
            layoutParams.width = width
            layoutParams.height = height
            x = coordinates.x
            y = coordinates.y
            tag = uniqueName
            id = View.generateViewId()                  //generate new ID, since adding fragment requires IDs
        }

        parent.addView(frameLayout)
        parent.invalidate()
        frameLayout.post {
            activity.supportFragmentManager.beginTransaction().replace(frameLayout.id, fragment, "frameLayoutSocials").commitAllowingStateLoss()
        }

        return frameLayout
    }

    fun userAchievementVisualization(activity: GameActivity, bitmapId: String, textTitle: String, textDescription: String, clickable: Boolean, textStats: String = ""): FrameLayout {
        return attachNewFragment(
                activity,
                FragmentAchievementVisualization.newInstance(bitmapId, textTitle, textDescription, textStats, clickable),
                activity.dm.widthPixels,
                activity.dm.heightPixels,
                Coordinates(0f, 0f)
        )
    }

    /**
     * SoundPool for short audio clips only, max 1 MB.
     * @since Alpha 0.5.0.2, DEV version
     * @author Jakub Kostka
     */
    fun playComponentSound(contextIn: Context? = null, raw: Int = R.raw.basic_click) {
        if (!Data.player.soundEffects) return
        var context = contextIn
        if(context == null && currentGameActivity != null){
            context = currentGameActivity
        }else if (context == null){
            return
        }

        val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        val sounds = SoundPool.Builder()
                .setAudioAttributes(attributes)
                .setMaxStreams(5)
                .build()

        val componentSound = sounds.load(context, raw, 2)       //raw has to processed beforehand

        sounds.setOnLoadCompleteListener { _, _, status ->
            Log.d("sounds_status", status.toString())
            if (status == 0) {
                sounds.play(componentSound, 1f, 1f, 1, 0, 1f)
            }
        }
    }

    class ItemDragListener(v: View, drawable: Int = 0) : View.DragShadowBuilder(v) {

        //creates new instance of the drawable, so it doesn't pass the reference of the ImageView and messes it up
        private val shadow = (view as? ImageView)?.drawable?.constantState?.newDrawable()       //v.context.getDrawable(drawable)

        // Defines a callback that sends the drag shadow dimensions and touch point back to the
        // system.
        override fun onProvideShadowMetrics(size: Point, touch: Point) {
            // Sets the width of the shadow to half the width of the original View
            val width: Int = view.width

            // Sets the height of the shadow to half the height of the original View
            val height: Int = view.height

            // The drag shadow is a ColorDrawable. This sets its dimensions to be the same as the
            // Canvas that the system will provide. As a result, the drag shadow will fill the
            // Canvas.
            shadow?.setBounds(0, 0, width, height)

            // Sets the size parameter's width and height values. These get back to the system
            // through the size parameter.
            size.set(width, height)

            // Sets the touch point's position to be in the middle of the drag shadow
            touch.set(width / 2, height / 2)
        }

        // Defines a callback that draws the drag shadow in a Canvas that the system constructs
        // from the dimensions passed in onProvideShadowMetrics().
        override fun onDrawShadow(canvas: Canvas) {
            // Draws the ColorDrawable in the Canvas passed in from the system.
            shadow?.draw(canvas)
        }
    }

    class StoryDragListener(val v: View?, val width: Int, val height: Int, val rotation: Float, val drawable: Drawable? = null) : View.DragShadowBuilder(v) {

        //creates new instance of the drawable, so it doesn't pass the reference of the ImageView and messes it up
        private val shadow = (view as? ImageView)?.drawable?.constantState?.newDrawable()?.mutate()
                ?: drawable?.mutate()
        private var minSize = hypot(((v?.width ?: width) / 2).toDouble(), ((v?.height
                ?: height) / 2).toDouble()) * 2

        // Defines a callback that sends the drag shadow dimensions and touch point back to the
        // system.
        override fun onProvideShadowMetrics(size: Point, touch: Point) {

            minSize = hypot(((v?.width ?: width) / 2).toDouble(), ((v?.height
                    ?: height) / 2).toDouble()) * 2

            touch.set((minSize / 2).toInt(), (minSize / 2).toInt())
            shadow?.setBounds(
                    ((minSize / 2) - ((v?.width ?: width) / 2)).toInt(),
                    ((minSize / 2) - ((v?.height ?: height) / 2)).toInt(),
                    ((minSize / 2) + ((v?.width ?: width) / 2)).toInt(),
                    ((minSize / 2) + ((v?.height ?: height) / 2)).toInt()
            )

            size.set(minSize.toInt(), minSize.toInt())
        }

        // Defines a callback that draws the drag shadow in a Canvas that the system constructs
        // from the dimensions passed in onProvideShadowMetrics().
        override fun onDrawShadow(canvas: Canvas) {
            // Draws the ColorDrawable in the Canvas passed in from the system.
            canvas.rotate(rotation, (minSize / 2).toFloat(), (minSize / 2).toFloat())
            shadow?.draw(canvas)
        }
    }

    fun vibrateAsError(context: Context, length: Long = 20) {
        if (Data.player.vibrateEffects) {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v!!.vibrate(VibrationEffect.createOneShot(length, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                v!!.vibrate(length)
            }
        }
    }

    data class MorseVibration(
            val timing: MutableList<Long>,
            val amplitudes: MutableList<Int>
    ) {
        var handlers: MutableList<Handler> = mutableListOf()

        fun addChar(timing: MutableList<Long>, amplitudes: MutableList<Int>) {
            this.timing.addAll(timing)
            this.amplitudes.addAll(amplitudes)
        }

        fun detachHandlers() {
            for (i in handlers) {
                i.removeCallbacksAndMessages(null)
            }
        }
    }

    //TODO vytvoř obdobu visualize reward baru nabízející vypnutí hudby, změny hlasitosti atd.
    fun respondOnMediaButton(context: Context) {
        Toast.makeText(context, "Shhhhh!", Toast.LENGTH_SHORT).show()
    }

    fun translateIntoMorse(text: String, textView: CustomTextView? = null): MorseVibration {
        val gapLetterLength: Long = 200     //174
        val gapWordLength: Long = 500       //1214

        val soundSpace: Long = 20
        val dit: Long = 60      //58
        val dah: Long = 200     //174
        val morseMap = hashMapOf(
                'a' to longArrayOf(dit, soundSpace, dah, soundSpace),
                'b' to longArrayOf(dah, soundSpace, dit, soundSpace, dit, soundSpace, dit, soundSpace),
                'c' to longArrayOf(dah, soundSpace, dit, soundSpace, dah, soundSpace, dit, soundSpace),
                'd' to longArrayOf(dah, soundSpace, dit, soundSpace, dit, soundSpace),
                'e' to longArrayOf(dit, soundSpace),
                'f' to longArrayOf(dit, soundSpace, dit, soundSpace, dah, soundSpace, dit, soundSpace),
                'g' to longArrayOf(dah, soundSpace, dah, soundSpace, dit, soundSpace),
                'h' to longArrayOf(dit, soundSpace, dit, soundSpace, dit, soundSpace, dit, soundSpace),
                'i' to longArrayOf(dit, soundSpace, dit, soundSpace),
                'j' to longArrayOf(dit, soundSpace, dah, soundSpace, dah, soundSpace, dah, soundSpace),
                'k' to longArrayOf(dah, soundSpace, dit, soundSpace, dah, soundSpace),
                'l' to longArrayOf(dit, soundSpace, dah, soundSpace, dit, soundSpace, dit, soundSpace),
                'm' to longArrayOf(dah, soundSpace, dah, soundSpace),
                'n' to longArrayOf(dah, soundSpace, dit, soundSpace),
                'o' to longArrayOf(dah, soundSpace, dah, soundSpace, dah, soundSpace),
                'p' to longArrayOf(dit, soundSpace, dah, soundSpace, dah, soundSpace, dit, soundSpace),
                'q' to longArrayOf(dah, soundSpace, dah, soundSpace, dit, soundSpace, dah, soundSpace),
                'r' to longArrayOf(dit, soundSpace, dah, soundSpace, dit, soundSpace),
                's' to longArrayOf(dit, soundSpace, dit, soundSpace, dit, soundSpace),
                't' to longArrayOf(dah, soundSpace),
                'u' to longArrayOf(dit, soundSpace, dit, soundSpace, dah, soundSpace),
                'w' to longArrayOf(dit, soundSpace, dah, soundSpace, dah, soundSpace),
                'x' to longArrayOf(dah, soundSpace, dit, soundSpace, dit, soundSpace, dah, soundSpace),
                'y' to longArrayOf(dah, soundSpace, dit, soundSpace, dah, soundSpace, dah, soundSpace),
                'z' to longArrayOf(dah, soundSpace, dah, soundSpace, dit, soundSpace, dit, soundSpace)
        )

        var lengthPrevious: Long = 100
        val morse = MorseVibration(mutableListOf(dah), mutableListOf(0))
        for (i in text.toLowerCase(Locale.ENGLISH)) {
            val newHandler = Handler()
            morse.handlers.add(newHandler)
            if (i == ' ') {
                morse.addChar(mutableListOf(gapWordLength), mutableListOf(0))

                lengthPrevious += gapWordLength
                newHandler.postDelayed({
                    textView?.setHTMLText("${textView.text} ")
                }, lengthPrevious)
            } else {
                val amplitudes = mutableListOf<Int>()
                for (j in 0 until (morseMap[i]?.toMutableList() ?: mutableListOf()).size / 2) {
                    amplitudes.add(255)
                    amplitudes.add(0)
                }
                morse.addChar(morseMap[i]?.toMutableList() ?: mutableListOf(), amplitudes)
                morse.addChar(mutableListOf(gapLetterLength), mutableListOf(0))

                lengthPrevious += (morseMap[i]?.toMutableList()
                        ?: mutableListOf()).sum() + gapLetterLength
                newHandler.postDelayed({
                    textView?.setHTMLText("${textView.text}$i")
                }, lengthPrevious)
            }
        }

        return morse
    }

    /**
     * Resolve X and Y for generated view beforehand, to find best place for it (majorly for pop-Ups)
     * @property activity: Activity - used for display metrics
     * @property x: Float - anchor X
     * @property y: Float - anchor Y
     * @property viewX: Int - measured/expected width of the view
     * @property viewY: Int - measured/expected height of the view
     * @since Alpha 0.5.0.1
     * @author Jakub Kostka
     */
    fun resolveLayoutLocation(activity: Activity, x: Float, y: Float, viewX: Int, viewY: Int): Coordinates {     //calculates the best location of dynamicly sized pop-up window and dynamicly placed click location
        val parent = activity.window.decorView.rootView

        return Coordinates(
                if (x >= parent.width - x) {
                    if (x - viewX < 0) {
                        10f
                    } else {
                        x - viewX
                    }
                } else {
                    if (x > parent.width) {
                        parent.width.toFloat()
                    } else {
                        x
                    }
                },

                if (y in parent.height / 2 * 0.8..parent.height / 2 * 1.2) {
                    ((parent.height / 2) - (viewY / 2)).toFloat()

                } else if (y >= parent.height / 2) {
                    if (y - viewY < 0) {
                        10f
                    } else {
                        y - viewY
                    }
                } else {
                    if (y + viewY > parent.height) {
                        parent.height - viewY.toFloat()
                    } else {
                        y
                    }
                }
                /*kotlin.math.max(kotlin.math.abs(x), kotlin.math.abs(parent.width - x)),
                kotlin.math.max(kotlin.math.abs(y), kotlin.math.abs(parent.height - y))*/
        )
    }

    fun attachShadowBlock(activity: GameActivity): ImageView{
        val parent: ViewGroup = activity.window.decorView.findViewById(android.R.id.content)

        parent.removeView(parent.findViewWithTag("shadowBlock"))

        val loadingBg = ImageView(activity)
        loadingBg.apply {
            tag = "shadowBlock"
            layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
            visibility = View.VISIBLE
            isClickable = true
            isFocusable = true
            elevation = 1f
            setImageResource(R.drawable.darken_background)
            alpha = 0.8f
        }

        parent.addView(loadingBg)
        return loadingBg
    }

    /**
     * Component (Fragment) overlay to show user's game properties (money, experience etc.).
     * Can be used for animations(throughout key Fragment object) and changes its values via animations ("fragmentBar.animateChanges()").
     *@property activity: Activity - used for display metrics and attaching generated view on the activity's main viewGroup
     *@property duration: Long?
     * @since Alpha 0.5.0.2, DEV version
     * @author Jakub Kostka
     */
    class GamePropertiesBar(
            val activity: GameActivity,
            val duration: Long? = null
    ) {
        private val parent: ViewGroup = activity.window.decorView.findViewById(android.R.id.content)
        val fragmentBar = FragmentGamePropertiesBar()
        val frameLayoutBar: FrameLayout = FrameLayout(parent.context)
        var isShown = false
        var attached = false

        fun updateProperties() {
            if (!isShown) {
                show()?.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        fragmentBar.animateChanges()
                    }
                })
            } else fragmentBar.animateChanges()
        }

        @SuppressLint("ClickableViewAccessibility")
        fun attach(): ValueAnimator? {
            if (attached) return null

            attached = true

            var clickableTemp = false
            var clickableHandler = Handler()
            var initialTouchX = 0f
            var originalX = (activity.dm.widthPixels * 0.25).toFloat()
            var validLongClick = false
            var validLongClickHandler = Handler()
            frameLayoutBar.apply {
                layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
                layoutParams.height = ((activity.dm.heightPixels * 0.1).toInt())
                layoutParams.width = (activity.dm.widthPixels * 0.5).toInt()
                y = (-(activity.dm.heightPixels * 0.1)).toFloat()
                x = Data.requestedBarX ?: originalX
                tag = "frameLayoutBar"
                id = View.generateViewId()                  //generate new ID, since adding fragment requires IDs
            }

            Handler().postDelayed({
                //fframeLayoutBar
                fragmentBar.getActionBackground()?.setOnTouchListener { _, motionEvent ->
                    when (motionEvent.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialTouchX = motionEvent.rawX

                            clickableHandler.removeCallbacksAndMessages(null)
                            clickableTemp = true
                            clickableHandler.postDelayed({
                                clickableTemp = false
                            }, 100)

                            validLongClickHandler.removeCallbacksAndMessages(null)
                            validLongClick = false
                            validLongClickHandler.postDelayed({
                                validLongClick = true
                                vibrateAsError(activity)
                            }, 600)

                            originalX = frameLayoutBar.x
                        }
                        MotionEvent.ACTION_UP -> {
                            if (clickableTemp) {
                                this@GamePropertiesBar.hide()
                            } else {
                                Data.requestedBarX = frameLayoutBar.x
                            }
                            validLongClickHandler.removeCallbacksAndMessages(null)
                            validLongClick = false
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            validLongClickHandler.removeCallbacksAndMessages(null)
                            validLongClick = false
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if(validLongClick){
                                val requestedX = (originalX + (motionEvent.rawX - initialTouchX))
                                frameLayoutBar.x = when {
                                    requestedX < 0 -> {
                                        0f
                                    }
                                    requestedX > activity.dm.widthPixels * 0.5 -> {
                                        (activity.dm.widthPixels * 0.5).toFloat()
                                    }
                                    else -> {
                                        requestedX
                                    }
                                }
                            }
                        }
                    }
                    true
                }
            }, 600)

            parent.addView(frameLayoutBar)
            parent.invalidate()
            frameLayoutBar.post {
                activity.supportFragmentManager.beginTransaction().replace(parent.findViewWithTag<FrameLayout>("frameLayoutBar").id, fragmentBar, "barFragment").commitAllowingStateLoss()
            }

            if (duration != null) {
                Handler().postDelayed({
                    this.detach()
                }, duration)
            }

            return show()
        }

        /**
         * animation not promised
         */
        fun hide(): ValueAnimator? {
            if (!isShown) return null

            isShown = false

            return ObjectAnimator.ofFloat(frameLayoutBar.y, (-(activity.dm.heightPixels * 0.1).toFloat())).apply {
                duration = 600
                addUpdateListener {
                    frameLayoutBar.y = it.animatedValue as Float
                }
                start()
            }
        }

        /**
         * animation not promised
         */
        fun show(): ValueAnimator? {
            if (isShown) return null

            return if (!attached) {
                attach()
            } else {
                isShown = true
                ObjectAnimator.ofFloat((-(activity.dm.heightPixels * 0.1)).toFloat(), 0f).apply {
                    duration = 600
                    addUpdateListener {
                        frameLayoutBar.y = it.animatedValue as Float
                    }
                    start()
                }
            }
        }

        fun detach() {
            if (!attached) return

            hide()?.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isShown = false
                    attached = false
                    parent.removeView(frameLayoutBar)
                }
            })
        }
    }

    fun loadBitmapFromView(v: View): Bitmap {
        val b = Bitmap.createBitmap(
        v.width, v.height, Bitmap.Config.ARGB_8888)
         val c = Canvas(b)
         v.layout(0, 0, v.width, v.height)
         v.draw(c)
      return b
     }

    /*
     * Call for uploading Bitmap to Google Firebase storage by specified identifier of the pathway
     * currently working only for production server
    fun uploadBitmapToStorage(bitmap: Bitmap, fileIdentifier: String, imageIdentifier: String): UploadTask {
        val storage = FirebaseStorage.getInstance()
        *
        val storageRef = storage.getReferenceFromUrl("gs://cubeit-build1.appspot.com")
        val finalStorageRef = storageRef.child("$fileIdentifier/$imageIdentifier.npg")
        *
        val byteStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.NPG, 100, byteStream)

        return storageRef.putBytes(byteStream.toByteArray())
    }*/

    /**
     * Animation overlay, primarily used to show damage dealt by any action in fight.
     *
     * @property activity: Activity - used for display metrics and attaching generated views on the activity's main viewGroup
     * @property startingPoint: Coordinates - use "getLocationOnScreen(IntArray(2))" to return correct position of the clicked view, regular x, y may not work.
     * @return ObjectAnimator. Override onAnimationEnd method to end the animation properly, or use native method ObjectAnimator.cancel().
     * @since Alpha 0.5.0.2, DEV version
     * @author Jakub Kostka
     */
    fun makeActionText(activity: GameActivity, startingPoint: Coordinates, text: String, color: Int = R.color.loginColor, sizeType: CustomTextView.SizeType = CustomTextView.SizeType.adaptive): ObjectAnimator {
        val parent = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

        val textView = CustomTextView(activity)
        textView.apply {
            fontSizeType = sizeType
            layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            visibility = View.VISIBLE
            setHTMLText("<b>$text</b>")
            tag = "actionText-${startingPoint.x}-${startingPoint.y}"
            setTextColor(activity.resources.getColor(color))
        }
        parent.addView(textView)

        textView.post {
            textView.x = startingPoint.x + textView.width / 2
            textView.invalidate()
            ObjectAnimator.ofFloat(startingPoint.y - textView.height / 2, (startingPoint.y - textView.height / 2) / 4).apply {
                duration = 600
                addUpdateListener {
                    textView.y = it.animatedValue as Float
                }
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}

                    override fun onAnimationEnd(animation: Animator) {
                        parent.removeView(textView)
                        Log.d("makeActionText", "post-ended, x: ${textView.x}, y: ${textView.y}, width: ${textView.width}, height: ${textView.height}")
                    }

                    override fun onAnimationCancel(animation: Animator) {}

                    override fun onAnimationRepeat(animation: Animator) {}
                })
                start()
            }
        }

        return ObjectAnimator()
    }

    class CubeItSnackbar(
            var background: String = "",
            var content: String = "",
            var durationMillis: Long = 4000,
            var hasBackground: Boolean = false,
            var showMessage: Boolean = false,
            var textColor: String = "white"

    ) : Serializable {
        var realBackground: Int = R.color.loginColor_4
            get() = Data.colorNameMap.getOrElse(background){ background.toIntOrNull() ?: 0 }
        var realTextColor: Int = R.color.bg_basic_white
            get() = Data.colorNameMap.getOrElse(textColor){ textColor.toIntOrNull() ?: 0}
    }

    /**
     * Animation overlay, primarily used to show important texts from game or server.
     *
     * @property activity: Activity - used for display metrics and attaching generated views on the activity's main viewGroup
     * @property durationMillis: Long - milliseconds as a duration of text being shown, animations are excluded from this property
     *
     * @return ObjectAnimator. Override onAnimationEnd method to end the animation properly, or use native method ObjectAnimator.cancel().
     * @since Alpha 0.5.0.4, DEV version
     * @author Jakub Kostka
     */
    @SuppressLint("ResourceAsColor")
    fun makeCubeItSnackbar(activity: GameActivity, text: String, durationMillis: Long = 4000, hasBackground: Boolean = false, backgroundColor: Int = Data.colorNameMap["black"]
            ?: 0, textColor: Int = Data.colorNameMap["white"]
            ?: 0, sizeType: CustomTextView.SizeType = CustomTextView.SizeType.title): ObjectAnimator {
        val parent = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

        val textView = CustomTextView(activity)
        textView.apply {
            fontSizeType = sizeType
            layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)

            elevation = 100f
            y = activity.dm.heightPixels.toFloat()
            x = 0f
            visibility = View.VISIBLE
            setTypeface(null, Typeface.BOLD)
            width = activity.dm.widthPixels
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setHTMLText("<b>$text</b>")
            tag = "cubeitsnackbar$text"
            setTextColor(textColor)

            if (hasBackground) {
                setBackgroundResource(R.drawable.bg_basic_white)
                setPadding(16, 8, 16, 8)
            } else setPadding(8, 0, 8, 0)
        }

        parent?.removeView(parent.findViewWithTag<CustomTextView>("cubeitsnackbar$text"))
        parent.addView(textView)

        textView.post {
            if (hasBackground) textView.background?.setColorFilter(backgroundColor, PorterDuff.Mode.SRC_ATOP)

            val disappearAnim = ObjectAnimator.ofFloat(((activity.dm.heightPixels * 0.5) - textView.height / 2).toFloat(), 0f).apply {
                duration = 1200
                startDelay = durationMillis
                addUpdateListener {
                    textView.y = it.animatedValue as Float
                    textView.alpha = (it.animatedValue as Float) / ((activity.dm.heightPixels * 0.5) - textView.height / 2).toFloat()
                }
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}

                    override fun onAnimationEnd(animation: Animator) {
                        parent.removeView(textView)
                    }

                    override fun onAnimationCancel(animation: Animator) {}

                    override fun onAnimationRepeat(animation: Animator) {}
                })
            }

            ObjectAnimator.ofFloat(activity.dm.heightPixels.toFloat(), ((activity.dm.heightPixels * 0.5) - textView.height / 2).toFloat()).apply {
                duration = 800
                addUpdateListener {
                    textView.y = it.animatedValue as Float
                    //textView.alpha = 1 - (it.animatedValue as Float - activity.dm.heightPixels) / abs(((activity.dm.heightPixels * 0.5) - textView.height / 2).toFloat() - activity.dm.heightPixels)
                }
                addListener(object : Animator.AnimatorListener {
                    override fun onAnimationStart(animation: Animator) {}

                    override fun onAnimationEnd(animation: Animator) {
                        disappearAnim.start()
                    }

                    override fun onAnimationCancel(animation: Animator) {}

                    override fun onAnimationRepeat(animation: Animator) {}
                })
                start()
            }
        }

        return ObjectAnimator()
    }

    /**
     * Animation overlay, universally used to show user's property bar with values and change them with animation.
     *
     * @property activity: Activity - used for display metrics and attaching generated views on the activity's main viewGroup
     * @property startingPoint: Coordinates - use "getLocationOnScreen(IntArray(2))" to return correct position of the clicked view, regular x, y may not work.
     * @return ObjectAnimator. Override onAnimationEnd method to end the animation properly, or use native method ObjectAnimator.cancel().
     * @since Alpha 0.5.0.2, DEV version
     * @author Jakub Kostka
     */
    fun visualizeReward(activity: GameActivity, startingPoint: Coordinates, reward: Reward?, existingPropertiesBar: GamePropertiesBar? = null): ValueAnimator? {
        val parent = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val activityWidth = activity.dm.widthPixels

        val propertiesBar = existingPropertiesBar ?: GamePropertiesBar(activity)

        val floatingCoins: MutableList<ImageView> = mutableListOf()
        val floatingXps: MutableList<ImageView> = mutableListOf()
        val floatingCubix: MutableList<ImageView> = mutableListOf()

        fun process() {
            if ((reward?.cubeCoins ?: 0) > 0) {
                for (i in 0 until nextInt(3, 7)) {
                    val currentCoin = ImageView(activity)

                    floatingCoins.add(i, ImageView(activity))
                    parent.addView(currentCoin)
                    parent.invalidate()

                    currentCoin.post {
                        currentCoin.apply {
                            setImageResource(R.drawable.coin_basic)

                            ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
                            layoutParams?.width = (activityWidth * 0.05).toInt()
                            layoutParams?.height = (activityWidth * 0.05).toInt()
                            visibility = View.VISIBLE
                        }
                    }

                    val newX = nextInt((startingPoint.x - (activityWidth * 0.075)).toInt(), (startingPoint.x + (activityWidth * 0.075)).toInt()).toFloat()
                    val newY = nextInt((startingPoint.y - (activityWidth * 0.075)).toInt(), (startingPoint.y + (activityWidth * 0.075)).toInt()).toFloat()

                    //travel animation CC
                    val travelXCC = ObjectAnimator.ofFloat(newX, propertiesBar.fragmentBar.getGlobalCoordsCubeCoins().x).apply {
                        duration = 600
                        addUpdateListener {
                            currentCoin.x = it.animatedValue as Float
                        }
                    }
                    val travelYCC = ObjectAnimator.ofFloat(newY, propertiesBar.fragmentBar.getGlobalCoordsCubeCoins().y).apply {
                        duration = 600
                        addUpdateListener {
                            currentCoin.y = it.animatedValue as Float
                        }
                        addListener(object : Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                parent.removeView(currentCoin)
                                propertiesBar.updateProperties()
                                Handler().postDelayed({
                                    if (propertiesBar.isShown && existingPropertiesBar == null) propertiesBar.detach()
                                }, 620)
                            }

                            override fun onAnimationCancel(animation: Animator) {}

                            override fun onAnimationRepeat(animation: Animator) {}
                        })
                    }

                    //spread animation CC
                    ObjectAnimator.ofFloat(startingPoint.x, newX).apply {
                        duration = 200
                        addUpdateListener {
                            currentCoin.x = it.animatedValue as Float
                        }
                        start()
                    }
                    ObjectAnimator.ofFloat(startingPoint.y, newY).apply {
                        duration = 200
                        addUpdateListener {
                            currentCoin.y = it.animatedValue as Float
                        }
                        addListener(object : Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                travelXCC.start()
                                travelYCC.start()
                            }

                            override fun onAnimationCancel(animation: Animator) {}

                            override fun onAnimationRepeat(animation: Animator) {}
                        })
                        start()
                    }
                }
            }

            if ((reward?.experience ?: 0) > 0) {
                for (i in 0 until nextInt(3, 7)) {
                    val currentXp = ImageView(activity)

                    floatingXps.add(i, ImageView(activity))
                    parent.addView(currentXp)
                    parent.invalidate()

                    currentXp.post {
                        currentXp.apply {
                            setImageResource(R.drawable.xp)

                            layoutParams!!.width = (activityWidth * 0.05).toInt()
                            layoutParams!!.height = (activityWidth * 0.05).toInt()
                            ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
                            visibility = View.VISIBLE
                        }
                    }

                    val newX = nextInt((startingPoint.x - (activityWidth * 0.075)).toInt(), (startingPoint.x + (activityWidth * 0.075)).toInt()).toFloat()
                    val newY = nextInt((startingPoint.y - (activityWidth * 0.075)).toInt(), (startingPoint.y + (activityWidth * 0.075)).toInt()).toFloat()

                    //travel animation XP
                    val travelXXP = ObjectAnimator.ofFloat(newX, propertiesBar.fragmentBar.getGlobalCoordsExperience().x).apply {
                        duration = 700
                        addUpdateListener {
                            currentXp.x = it.animatedValue as Float
                        }
                    }
                    val travelYXP = ObjectAnimator.ofFloat(newY, propertiesBar.fragmentBar.getGlobalCoordsExperience().y).apply {
                        duration = 700
                        addUpdateListener {
                            currentXp.y = it.animatedValue as Float
                        }
                        addListener(object : Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                parent.removeView(currentXp)
                                propertiesBar.updateProperties()
                                Handler().postDelayed({
                                    if (propertiesBar.isShown && existingPropertiesBar == null) propertiesBar.detach()
                                }, 700)
                            }

                            override fun onAnimationCancel(animation: Animator) {}

                            override fun onAnimationRepeat(animation: Animator) {}
                        })
                    }

                    //spread animation XP
                    ObjectAnimator.ofFloat(startingPoint.x, newX).apply {
                        duration = 300
                        addUpdateListener {
                            currentXp.x = it.animatedValue as Float
                        }
                        start()
                    }
                    ObjectAnimator.ofFloat(startingPoint.y, newY).apply {
                        duration = 300
                        addUpdateListener {
                            currentXp.y = it.animatedValue as Float
                        }
                        addListener(object : Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                travelXXP.start()
                                travelYXP.start()
                            }

                            override fun onAnimationCancel(animation: Animator) {}

                            override fun onAnimationRepeat(animation: Animator) {}
                        })
                        start()
                    }
                }
            }

            if ((reward?.cubix ?: 0) > 0) {
                for (i in 0 until nextInt(3, 7)) {
                    val currentCubix = ImageView(activity)

                    floatingCubix.add(i, ImageView(activity))
                    parent.addView(currentCubix)
                    parent.invalidate()

                    currentCubix.post {
                        currentCubix.apply {
                            setImageResource(R.drawable.cubix)

                            layoutParams!!.width = (activityWidth * 0.05).toInt()
                            layoutParams!!.height = (activityWidth * 0.05).toInt()
                            ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
                            visibility = View.VISIBLE
                        }
                    }

                    val newX = nextInt((startingPoint.x - (activityWidth * 0.075)).toInt(), (startingPoint.x + (activityWidth * 0.075)).toInt()).toFloat()
                    val newY = nextInt((startingPoint.y - (activityWidth * 0.075)).toInt(), (startingPoint.y + (activityWidth * 0.075)).toInt()).toFloat()

                    //travel animation Cubix
                    val travelXCubix = ObjectAnimator.ofFloat(newX, propertiesBar.fragmentBar.getGlobalCoordsCubix().x).apply {
                        duration = 700
                        addUpdateListener {
                            currentCubix.x = it.animatedValue as Float
                        }
                    }
                    val travelYCubix = ObjectAnimator.ofFloat(newY, propertiesBar.fragmentBar.getGlobalCoordsCubix().y).apply {
                        duration = 700
                        addUpdateListener {
                            currentCubix.y = it.animatedValue as Float
                        }
                        addListener(object : Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                parent.removeView(currentCubix)
                                propertiesBar.updateProperties()
                                Handler().postDelayed({
                                    if (propertiesBar.isShown && existingPropertiesBar == null) propertiesBar.detach()
                                }, 700)
                            }

                            override fun onAnimationCancel(animation: Animator) {}

                            override fun onAnimationRepeat(animation: Animator) {}
                        })
                    }

                    //spread animation Cubix
                    ObjectAnimator.ofFloat(startingPoint.x, newX).apply {
                        duration = 300
                        addUpdateListener {
                            currentCubix.x = it.animatedValue as Float
                        }
                        start()
                    }
                    ObjectAnimator.ofFloat(startingPoint.y, newY).apply {
                        duration = 300
                        addUpdateListener {
                            currentCubix.y = it.animatedValue as Float
                        }
                        addListener(object : Animator.AnimatorListener {
                            override fun onAnimationStart(animation: Animator) {}

                            override fun onAnimationEnd(animation: Animator) {
                                travelXCubix.start()
                                travelYCubix.start()
                            }

                            override fun onAnimationCancel(animation: Animator) {}

                            override fun onAnimationRepeat(animation: Animator) {}
                        })
                        start()
                    }
                }
            }

            Handler().postDelayed({
                reward?.receive(null, activity)
            }, 400)
        }

        if (propertiesBar.isShown) {
            process()
        } else {
            propertiesBar.attach()?.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    process()
                }
            })
        }
        return ObjectAnimator.ofFloat(0f, 0f).apply {
            duration = 1500
            start()
        }
    }

    class OverlayOptionsMenuItem(
            var bitmapId: String = "",
            var clickListener: View.OnClickListener = View.OnClickListener {},
            var text: String = ""
    )

    enum class InitDynamicAnimation{
        SCALE_FROM_SMALL,
        NONE
    }

    class OverlayOptionsMenu(
            var activity: GameActivity
    ){
        private var imageViewBackground: ImageView = ImageView(activity)
        private var recyclerView: RecyclerView = RecyclerView(activity)
        private var imageViewClose: ImageView? = null

        private fun animateClosing(dynamicAnimation: InitDynamicAnimation = InitDynamicAnimation.NONE){
            when(dynamicAnimation){
                InitDynamicAnimation.SCALE_FROM_SMALL -> {
                    ObjectAnimator.ofFloat(1f, 0.1f).apply {
                        duration = 400
                        addUpdateListener {
                            recyclerView.scaleX = it.animatedValue as Float
                            recyclerView.scaleY = it.animatedValue as Float
                            imageViewBackground.scaleX = it.animatedValue as Float
                            imageViewBackground.scaleY = it.animatedValue as Float
                        }
                        addListener(object : AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: Animator?) {
                                super.onAnimationEnd(animation)
                                this@OverlayOptionsMenu.remove()
                            }

                            override fun onAnimationStart(animation: Animator?) {
                                super.onAnimationStart(animation)
                                imageViewClose?.visibility = View.GONE
                            }
                        })
                        start()
                    }
                }
                else -> this.remove()
            }
        }

        fun create(options: List<OverlayOptionsMenuItem>, closeable: Boolean = true, dynamicAnimation: InitDynamicAnimation = InitDynamicAnimation.NONE) {
            remove()
            val parent = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

            imageViewBackground.apply {
                tag = "overlayOptionsMenuBg"
                layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
                visibility = View.VISIBLE
                scaleX = 0.1f
                scaleY = 0.1f
                isClickable = true
                isFocusable = true
                setImageResource(R.drawable.darken_background)
                alpha = 0.8f
                setOnClickListener {
                    if(closeable) animateClosing(dynamicAnimation)
                }
            }
            recyclerView.apply {
                tag = "overlayOptionsMenuRecycler"
                layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
                visibility = View.VISIBLE
                isClickable = true
                isFocusable = true
                scaleX = 0.1f
                scaleY = 0.1f
                z = 2f
                x = (activity.dm.widthPixels * 0.1).toFloat()
                y = (activity.dm.heightPixels * 0.1).toFloat()
                layoutParams.width = (activity.dm.widthPixels * 0.8).toInt()
                layoutParams.height = (activity.dm.heightPixels * 0.9).toInt()
                layoutManager = LinearLayoutManager(activity)
                adapter = DialogOptionPickerRecyclerView(options)
            }

            if(closeable){
                imageViewClose = ImageView(activity)
                imageViewClose?.apply {
                    tag = "overlayOptionsMenuClose"
                    layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
                    visibility = View.INVISIBLE
                    isClickable = true
                    isFocusable = true
                    z = 4f
                    x = (activity.dm.widthPixels * 0.92).toFloat()
                    y = 6f
                    layoutParams.width = (activity.dm.widthPixels * 0.05).toInt()
                    layoutParams.height = (activity.dm.widthPixels * 0.05).toInt()
                    setImageResource(R.drawable.cross_button_white)
                    setOnClickListener {
                        animateClosing()
                    }
                }
                Log.d("imageViewClose", "called")
                parent.addView(imageViewClose)
            }

            parent.addView(imageViewBackground)
            parent.addView(recyclerView)

            recyclerView.post {
                when(dynamicAnimation){
                    InitDynamicAnimation.SCALE_FROM_SMALL -> {
                        ObjectAnimator.ofFloat(0.1f, 1f).apply {
                            duration = 400
                            addUpdateListener {
                                recyclerView.scaleX = it.animatedValue as Float
                                recyclerView.scaleY = it.animatedValue as Float
                                imageViewBackground.scaleX = it.animatedValue as Float
                                imageViewBackground.scaleY = it.animatedValue as Float
                            }
                            addListener(object : AnimatorListenerAdapter() {
                                override fun onAnimationEnd(animation: Animator?) {
                                    super.onAnimationEnd(animation)
                                    imageViewClose?.visibility = View.VISIBLE
                                }

                                override fun onAnimationStart(animation: Animator?) {
                                    super.onAnimationStart(animation)
                                    imageViewClose?.visibility = View.GONE
                                }
                            })
                            start()
                        }
                    }
                }
            }
        }

        fun remove(){
            val parent = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            parent.removeView(parent.findViewWithTag("overlayOptionsMenuBg"))
            parent.removeView(parent.findViewWithTag("overlayOptionsMenuRecycler"))
            parent.removeView(parent.findViewWithTag("overlayOptionsMenuClose"))
        }
    }


    /**
     * Loading screen overlay, universally used to make user wait, with having just darken background, not entire screen.
     *
     * @property activity: Activity - used for display metrics and attaching generated views on the activity's main viewGroup
     * @return ObjectAnimator. Override onAnimationEnd method to end the animation properly, or use native method ObjectAnimator.cancel().
     * @since Alpha 0.5.0.2, DEV version
     * @author Jakub Kostka
     */
    fun createLoading(activity: GameActivity, startAutomatically: Boolean = true, cancelable: Boolean = false, listener: View.OnClickListener? = null): ObjectAnimator {
        val parent = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val context = parent.context

        val activityWidth = activity.dm.widthPixels

        val loadingBg = ImageView(context)
        loadingBg.apply {
            tag = "customLoadingBg"
            layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_PARENT, ConstraintLayout.LayoutParams.MATCH_PARENT)
            visibility = View.VISIBLE
            isClickable = true
            isFocusable = true
            setImageResource(R.drawable.darken_background)
            alpha = 0.8f
        }

        val loadingImage = ImageView(context)
        loadingImage.apply {
            tag = "customLoadingImage"
            setImageResource(R.drawable.icon_web)
            visibility = View.VISIBLE
            layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
            layoutParams.width = (activityWidth * 0.1).toInt()
            layoutParams.height = (activityWidth * 0.1).toInt()
            x = ((activityWidth / 2 - (activityWidth * 0.1 / 2).toInt()).toFloat())
            y = (activityWidth * 0.05).toFloat()
        }

        parent.addView(loadingBg)
        parent.addView(loadingImage)
        if (cancelable) {
            val loadingCancel = Button(context, null, 0, R.style.AppTheme_Button)
            loadingCancel.apply {
                tag = "customLoadingCancel"
                visibility = View.VISIBLE
                text = "cancel"
                layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
                x = ((activityWidth / 2 - (activityWidth * 0.1 / 2).toInt()).toFloat())
                y = (activity.dm.heightPixels * 0.4).toFloat()
                setOnClickListener(listener)
            }
            parent.addView(loadingCancel)
        }

        val rotateAnimation: ObjectAnimator = ObjectAnimator.ofFloat(loadingImage,
                "rotation", 0f, 360f)

        rotateAnimation.addListener(object : AnimatorListenerAdapter() {

            override fun onAnimationEnd(animation: Animator?) {
                super.onAnimationEnd(animation)
                parent.removeView(parent.findViewWithTag<ImageView>("customLoadingImage"))
                parent.removeView(parent.findViewWithTag<FrameLayout>("customLoadingBg"))
                if (cancelable) parent.removeView(parent.findViewWithTag<Button>("customLoadingCancel"))
            }

            /*override fun onAnimationStart(animation: Animator?) {
                super.onAnimationStart(animation)
                loadingBg.bringToFront()
                loadingImage.bringToFront()
            }*/
        })
        rotateAnimation.duration = 900
        rotateAnimation.repeatCount = Animation.INFINITE

        if (startAutomatically) loadingImage.post {
            rotateAnimation.start()
        }

        return rotateAnimation
    }

    class PropertiesOptions(
            val editTextWidth: EditText? = null,
            val editTextHeight: EditText? = null,
            val textViewBringOnTop: CustomTextView? = null,
            val editTextRotation: EditText? = null,
            val switchAnimate: Switch? = null,
            val textViewDelete: CustomTextView? = null,
            val editTextContent: CustomEditText? = null,
            val spinnerFont: Spinner? = null,
            val imageViewBg: ImageView? = null,
            val textColorManager: ImageView? = null,
            val switchTextBg: Switch? = null,
            val textBgColorManager: ImageView? = null,
            val imageViewWidthLock: ImageView? = null,
            val imageViewHeightLock: ImageView? = null
    ) : Serializable

    enum class PropertiesOptionsGravity{
        CENTER,
        LEFT,
        RIGHT
    }

    @SuppressLint("ClickableViewAccessibility")
    fun attachPropertiesOptions(component: FrameworkComponent, view: View, activity: GameActivity, anchorCoordinates: Coordinates, rotation: Boolean = false, switch: Boolean = false, gravity: PropertiesOptionsGravity, hostFrameLayout: FrameLayout? = null): FrameLayout {      //TODO test
        val parent = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        var frameLayout = FrameLayout(activity)
        var initialX = 0f
        var initialY = 0f
        var fragment = Fragment_FrameworkPropertiesOptions()

        if(hostFrameLayout == null){
            parent.removeView(parent.findViewWithTag("PropertiesOptionsPopUp"))

            val generatedID = View.generateViewId()
            frameLayout.apply {
                layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.WRAP_CONTENT, ConstraintLayout.LayoutParams.WRAP_CONTENT)
                x = anchorCoordinates.x
                y = anchorCoordinates.y
                id = generatedID
                elevation = 2f
                tag = "PropertiesOptionsPopUp"
            }

            parent.addView(frameLayout)
        }else {
            frameLayout = hostFrameLayout
            fragment = Fragment_FrameworkPropertiesOptions.newInstance(rotation, switch, component, false)
            activity.supportFragmentManager
                    .beginTransaction()
                    .replace(hostFrameLayout.id, fragment, "FragmentPropertiesOptionsPopUp")
                    .commitAllowingStateLoss()
        }

        frameLayout.post {
            if(hostFrameLayout == null){
                if (parent.findViewWithTag<FrameLayout>("PropertiesOptionsPopUp")?.id == null) return@post

                fragment = Fragment_FrameworkPropertiesOptions.newInstance(rotation, switch, component, true)
                activity.supportFragmentManager
                        .beginTransaction()
                        .replace(parent.findViewWithTag<FrameLayout>("PropertiesOptionsPopUp").id, fragment, "FragmentPropertiesOptionsPopUp")
                        .commitAllowingStateLoss()
            }

            Handler().postDelayed({
                val properties = fragment.getPropertiesOptions()

                //move framelayout around
                if(hostFrameLayout == null){
                    properties.imageViewBg?.setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN -> {
                                initialX = event.x
                                initialY = event.y
                            }
                            MotionEvent.ACTION_MOVE -> {
                                frameLayout.x = event.rawX - initialX
                                frameLayout.y = event.rawY - initialY
                                frameLayout.requestLayout()
                            }
                        }
                        true
                    }
                }

                //ratio locker
                if(component.lockedOn == FrameworkComponentLocker.WIDTH){
                    properties.imageViewWidthLock?.alpha = 1f
                    properties.editTextHeight?.isEnabled = false
                }else if(component.lockedOn == FrameworkComponentLocker.HEIGHT){
                    properties.imageViewHeightLock?.alpha = 1f
                    properties.editTextWidth?.isEnabled = false
                }
                properties.imageViewWidthLock?.setOnClickListener {
                    if(component.lockedOn == FrameworkComponentLocker.WIDTH){
                        component.lockedOn = FrameworkComponentLocker.NONE
                        properties.imageViewWidthLock.alpha = 0.2f
                        properties.editTextHeight?.isEnabled = true
                    }else {
                        properties.imageViewHeightLock?.alpha = 0.2f
                        properties.imageViewWidthLock.alpha = 1f
                        properties.editTextWidth?.isEnabled = true
                        properties.editTextHeight?.isEnabled = false
                        component.lockedOn = FrameworkComponentLocker.WIDTH
                    }
                    component.update(activity)
                    properties.editTextWidth?.setText(component.width.roundToInt().toString())
                }
                properties.imageViewHeightLock?.setOnClickListener {
                    if(component.lockedOn == FrameworkComponentLocker.HEIGHT){
                        component.lockedOn = FrameworkComponentLocker.NONE
                        properties.imageViewHeightLock.alpha = 0.2f
                        properties.editTextWidth?.isEnabled = true
                    }else {
                        properties.imageViewWidthLock?.alpha = 0.2f
                        properties.imageViewHeightLock.alpha = 1f
                        properties.editTextHeight?.isEnabled = true
                        properties.editTextWidth?.isEnabled = false
                        component.lockedOn = FrameworkComponentLocker.HEIGHT
                    }
                    component.update(activity)
                    properties.editTextHeight?.setText(component.height.roundToInt().toString())
                }

                //text and background color pickers
                properties.switchTextBg?.setOnCheckedChangeListener { _, isChecked ->
                    properties.textBgColorManager?.isEnabled = isChecked
                    component.hasBackground = isChecked
                    component.update(activity)
                }
                properties.textBgColorManager?.setOnClickListener {
                    val viewP = activity.layoutInflater.inflate(R.layout.popup_dialog_recyclerview, null, false)
                    val window = PopupWindow(viewP, (activity.dm.heightPixels * 1.2).toInt(), activity.dm.heightPixels)
                    window.isOutsideTouchable = false
                    window.isFocusable = true
                    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                    viewP.textViewDialogRecyclerSampleText.visibility = View.VISIBLE
                    viewP.buttonDialogRecyclerOk.visibility = View.VISIBLE
                    viewP.textViewDialogRecyclerSampleText.setTextColor(Data.colorNameMap[component.textColor]
                            ?: R.color.black)

                    viewP.buttonDialogRecyclerOk.setOnClickListener {
                        val chosenColor = (viewP.recyclerViewDialogRecycler.adapter as? DialogColorPickerRecyclerView)?.innerChosenColor
                                ?: ""
                        viewP.textViewDialogRecyclerSampleText.setTextColor(Data.colorNameMap[chosenColor]
                                ?: R.color.black)
                        Log.d("choosing_color", "chosen color is $chosenColor")
                        component.backgroundColor = chosenColor
                        window.dismiss()
                    }
                    viewP.recyclerViewDialogRecycler.apply {
                        layoutManager = LinearLayoutManager(activity)
                        adapter = DialogColorPickerRecyclerView(component.backgroundColor, viewP.textViewDialogRecyclerSampleText)
                    }
                    viewP.imageViewDialogRecyclerClose.setOnClickListener {
                        window.dismiss()
                    }
                    window.setOnDismissListener {
                        component.update(activity)
                    }
                    window.showAtLocation(viewP, Gravity.CENTER, 0, 0)
                }
                properties.textColorManager?.setOnClickListener {
                    val viewP = activity.layoutInflater.inflate(R.layout.popup_dialog_recyclerview, null, false)
                    val window = PopupWindow(viewP, (activity.dm.heightPixels * 1.2).toInt(), activity.dm.heightPixels)
                    window.isOutsideTouchable = false
                    window.isFocusable = true
                    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

                    viewP.textViewDialogRecyclerSampleText.visibility = View.VISIBLE
                    viewP.buttonDialogRecyclerOk.visibility = View.VISIBLE
                    viewP.textViewDialogRecyclerSampleText.setTextColor(Data.colorNameMap[component.textColor]
                            ?: R.color.black)

                    viewP.buttonDialogRecyclerOk.setOnClickListener {
                        val chosenColor = (viewP.recyclerViewDialogRecycler.adapter as? DialogColorPickerRecyclerView)?.innerChosenColor
                                ?: ""
                        viewP.textViewDialogRecyclerSampleText.setTextColor(Data.colorNameMap[chosenColor]
                                ?: R.color.black)
                        Log.d("choosing_color", "chosen color is $chosenColor")
                        component.textColor = chosenColor
                        window.dismiss()
                    }
                    viewP.recyclerViewDialogRecycler.apply {
                        layoutManager = LinearLayoutManager(activity)
                        adapter = DialogColorPickerRecyclerView(component.textColor, viewP.textViewDialogRecyclerSampleText)
                    }
                    viewP.imageViewDialogRecyclerClose.setOnClickListener {
                        window.dismiss()
                    }
                    window.setOnDismissListener {
                        component.update(activity)
                    }
                    window.showAtLocation(viewP, Gravity.CENTER, 0, 0)
                }

                //control buttons
                properties.textViewBringOnTop?.setOnClickListener {
                    (activity as? Activity_Create_Story)?.bringComponentToFront(component)
                    component.view?.bringToFront()
                    frameLayout.bringToFront()
                }
                properties.textViewDelete?.setOnClickListener {
                    (activity as? Activity_Create_Story)?.apply {
                        removeComponent(component)
                        removeCurrentPropertiesOptions()
                    }
                }

                //font size type
                if (component.type == FrameworkComponentType.TEXT) {
                    properties.editTextContent?.visibility = View.VISIBLE
                    properties.spinnerFont?.visibility = View.VISIBLE

                    ArrayAdapter.createFromResource(
                            activity,
                            R.array.font_size,
                            R.layout.spinner_light_item
                    ).also { adapter ->
                        adapter.setDropDownViewResource(R.layout.spinner_light_item)
                        properties.spinnerFont?.adapter = adapter
                    }

                    properties.spinnerFont?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                        override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                            Log.d("spinnerFont", position.toString())
                            component.fontSizeType = when (position) {
                                0 -> CustomTextView.SizeType.small
                                2 -> CustomTextView.SizeType.smallTitle
                                3 -> CustomTextView.SizeType.title
                                else -> CustomTextView.SizeType.adaptive        //position 1
                            }
                            component.update(activity)
                        }

                        override fun onNothingSelected(parent: AdapterView<*>) {
                        }
                    }
                }
                properties.spinnerFont?.setSelection(when (component.fontSizeType) {
                    CustomTextView.SizeType.small -> 0
                    CustomTextView.SizeType.smallTitle -> 2
                    CustomTextView.SizeType.title -> 3
                    else -> 1        //position 1
                })

                //edittexts listeners
                properties.editTextContent?.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                        component.textContent = s?.toString() ?: ""
                        component.update(activity)
                        parent.invalidate()
                    }

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        properties.editTextContent.requestLayout()
                    }
                })

                properties.editTextWidth?.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                    }

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        component.width = if (s.isNullOrEmpty()) {
                            60f
                        } else (min(max(properties.editTextWidth.text.toString().toIntOrNull()?.toFloat()
                                ?: 60f, 0f), 100f))
                        component.update(activity)
                        parent.invalidate()

                        if(component.lockedOn == FrameworkComponentLocker.WIDTH) properties.editTextHeight?.setText(component.height.toInt().toString())
                    }
                })

                properties.editTextHeight?.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                    }

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        component.height = if (s.isNullOrEmpty()) {
                            60f
                        } else (min(max(properties.editTextHeight.text.toString().toFloatOrNull()
                                ?: 60f, 0f), 100f))
                        component.update(activity)
                        parent.invalidate()

                        if(component.lockedOn == FrameworkComponentLocker.HEIGHT) properties.editTextWidth?.setText(component.width.toInt().toString())
                    }
                })

                properties.editTextRotation?.addTextChangedListener(object : TextWatcher {
                    override fun afterTextChanged(s: Editable?) {
                    }

                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        component.rotationAngle = if (s.isNullOrEmpty()) {
                            0f
                        } else (properties.editTextRotation.text.toString().toFloatOrNull()
                                ?: 0f)
                        component.update(activity)
                        parent.invalidate()
                    }
                })

                properties.switchAnimate?.setOnCheckedChangeListener { _, isChecked ->
                    component.animate = isChecked
                }
            }, 50)

            if(hostFrameLayout == null){
                Handler().postDelayed({
                    val measurements = frameLayout.getMeasurements()
                    Log.d("measuredProperties", measurements.toString())

                    val travelXTo = when {
                        gravity == PropertiesOptionsGravity.LEFT -> {
                            anchorCoordinates.x - frameLayout.width
                        }
                        anchorCoordinates.x + frameLayout.width > activity.dm.widthPixels -> {
                            var finalX = component.absoluteCoordinates.x - frameLayout.width - 4f
                            if(finalX < 0){
                                finalX = 0f
                            }
                            finalX
                        }
                        anchorCoordinates.x < 0 -> {
                            component.absoluteCoordinates.x + component.realWidth
                        }
                        gravity == PropertiesOptionsGravity.CENTER -> {
                            frameLayout.x - view.width / 2
                        }
                        else -> {
                            frameLayout.x
                        }
                    }

                    ObjectAnimator.ofFloat(frameLayout.x, travelXTo).apply {
                        duration = 100
                        addUpdateListener {
                            frameLayout.x = it.animatedValue as Float
                        }
                        start()
                    }

                    if (anchorCoordinates.y + frameLayout.height > activity.dm.heightPixels) {
                        ObjectAnimator.ofFloat(frameLayout.y, activity.dm.heightPixels.toFloat() - frameLayout.height).apply {
                            duration = 100
                            addUpdateListener {
                                frameLayout.y = it.animatedValue as Float
                            }
                            start()
                        }
                    }
                }, 250)
            }
        }

        return frameLayout
    }

    private class DialogOptionPickerRecyclerView(val options: List<OverlayOptionsMenuItem>) :
            RecyclerView.Adapter<DialogOptionPickerRecyclerView.CategoryViewHolder>() {
        var inflater: View? = null

        class CategoryViewHolder(
                val rowIconSlot1: ImageView,
                val rowIconSlot2: ImageView,
                val rowIconSlot3: ImageView,
                val rowIconSlot4: ImageView,
                val rowIconSlot5: ImageView,
                val textViewSlot1: CustomTextView,
                val textViewSlot2: CustomTextView,
                val textViewSlot3: CustomTextView,
                val textViewSlot4: CustomTextView,
                val textViewSlot5: CustomTextView,
                inflater: View,
                val viewGroup: ViewGroup
        ) : RecyclerView.ViewHolder(inflater)

        override fun getItemCount() = options.size / 5 + 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            inflater = LayoutInflater.from(parent.context).inflate(R.layout.row_5_icons_text, parent, false)
            return CategoryViewHolder(
                    inflater!!.rowIconTextSlot1,
                    inflater!!.rowIconTextSlot2,
                    inflater!!.rowIconTextSlot3,
                    inflater!!.rowIconTextSlot4,
                    inflater!!.rowIconTextSlot5,
                    inflater!!.textViewRowIconText1,
                    inflater!!.textViewRowIconText2,
                    inflater!!.textViewRowIconText3,
                    inflater!!.textViewRowIconText4,
                    inflater!!.textViewRowIconText5,
                    inflater ?: LayoutInflater.from(parent.context).inflate(R.layout.row_5_icons_text, parent, false),
                    parent
            )
        }

        override fun onBindViewHolder(viewHolder: CategoryViewHolder, position: Int) {
            val indexAdapter: Int = if (position == 0) 0 else {
                position * 5
            }

            class Node(
                    image: ImageView,
                    textView: CustomTextView,
                    index: Int
            ) {
                init {
                    image.apply {
                        if (index < options.size) {
                            setImageBitmap(Data.downloadedBitmaps[options[index].bitmapId])
                            setOnClickListener(options[index].clickListener)
                        } else {
                            visibility = View.GONE
                        }
                    }
                    textView.apply {
                        if (index < options.size) {
                            setHTMLText(options[index].text)
                        }else {
                            visibility = View.GONE
                        }
                    }
                }
            }

            Node(viewHolder.rowIconSlot1, viewHolder.textViewSlot1, indexAdapter)
            Node(viewHolder.rowIconSlot2, viewHolder.textViewSlot2, indexAdapter + 1)
            Node(viewHolder.rowIconSlot3, viewHolder.textViewSlot3, indexAdapter + 2)
            Node(viewHolder.rowIconSlot4, viewHolder.textViewSlot4, indexAdapter + 3)
            Node(viewHolder.rowIconSlot5, viewHolder.textViewSlot5, indexAdapter + 4)
        }
    }

    private class DialogColorPickerRecyclerView(chosenColor: String, var sampleText: CustomTextView, val colors: MutableList<String> = Data.colorNameMap.keys.toMutableList()) :
            RecyclerView.Adapter<DialogColorPickerRecyclerView.CategoryViewHolder>() {

        var innerChosenColor: String = chosenColor
        var inflater: View? = null

        class CategoryViewHolder(
                val rowIconSlot1: ImageView,
                val rowIconSlot2: ImageView,
                val rowIconSlot3: ImageView,
                val rowIconSlot4: ImageView,
                val rowIconSlot5: ImageView,
                inflater: View,
                val viewGroup: ViewGroup
        ) : RecyclerView.ViewHolder(inflater)

        override fun getItemCount() = colors.size / 5 + 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            inflater = LayoutInflater.from(parent.context).inflate(R.layout.row_5_icons, parent, false)
            return CategoryViewHolder(
                    inflater!!.rowIconSlot1,
                    inflater!!.rowIconSlot2,
                    inflater!!.rowIconSlot3,
                    inflater!!.rowIconSlot4,
                    inflater!!.rowIconSlot5,
                    inflater
                            ?: LayoutInflater.from(parent.context).inflate(R.layout.row_5_icons, parent, false),
                    parent
            )
        }

        override fun onBindViewHolder(viewHolder: CategoryViewHolder, position: Int) {
            val indexAdapter: Int = if (position == 0) 0 else {
                position * 5
            }

            class Node(
                    image: ImageView,
                    index: Int
            ) {
                init {
                    image.apply {
                        if (index < colors.size) {
                            setColorFilter(Data.colorNameMap.getOrElse(colors[index]){ colors[index].toIntOrNull() ?: 0})

                            alpha = if (innerChosenColor == colors[index]) {
                                setPadding(8, 8, 8, 8)
                                0.5f
                            } else {
                                setPadding(0, 0, 0, 0)
                                1f
                            }

                            setOnClickListener {
                                innerChosenColor = colors[index]
                                setPadding(8, 8, 8, 8)
                                sampleText.setTextColor(Data.colorNameMap.getOrElse(colors[index]){ colors[index].toIntOrNull() ?: 0})
                                notifyDataSetChanged()
                            }
                        } else {
                            visibility = View.GONE
                        }
                    }
                }
            }

            Node(viewHolder.rowIconSlot1, indexAdapter)
            Node(viewHolder.rowIconSlot2, indexAdapter + 1)
            Node(viewHolder.rowIconSlot3, indexAdapter + 2)
            Node(viewHolder.rowIconSlot4, indexAdapter + 3)
            Node(viewHolder.rowIconSlot5, indexAdapter + 4)
        }
    }

    class DialogSquareBitmapPicker(val parent: Context, chosenId: String, val bitmaps: List<String>) :
            RecyclerView.Adapter<DialogSquareBitmapPicker.CategoryViewHolder>() {

        var innerChosenBitmap: String = chosenId
        var inflater: View? = null

        class CategoryViewHolder(
                val rowIconSlot1: ImageView,
                val rowIconSlot2: ImageView,
                val rowIconSlot3: ImageView,
                val rowIconSlot4: ImageView,
                val rowIconSlot5: ImageView,
                inflater: View,
                val viewGroup: ViewGroup
        ) : RecyclerView.ViewHolder(inflater)

        override fun getItemCount() = bitmaps.size / 5 + 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            inflater = LayoutInflater.from(parent.context).inflate(R.layout.row_5_icons, parent, false)
            return CategoryViewHolder(
                    inflater!!.rowIconSlot1,
                    inflater!!.rowIconSlot2,
                    inflater!!.rowIconSlot3,
                    inflater!!.rowIconSlot4,
                    inflater!!.rowIconSlot5,
                    inflater
                            ?: LayoutInflater.from(parent.context).inflate(R.layout.row_5_icons, parent, false),
                    parent
            )
        }

        override fun onBindViewHolder(viewHolder: CategoryViewHolder, position: Int) {
            val indexAdapter: Int = if (position == 0) 0 else {
                position * 5
            }

            class Node(
                    image: ImageView,
                    index: Int
            ) {
                init {
                    image.apply {
                        if (index < bitmaps.size) {
                            val dr: RoundedBitmapDrawable = RoundedBitmapDrawableFactory.create(this@DialogSquareBitmapPicker.parent.resources, Data.downloadedBitmaps[bitmaps[index]])
                            dr.cornerRadius = 15f
                            dr.isCircular = true
                            setImageDrawable(dr)

                            alpha = if (innerChosenBitmap == bitmaps[index]) {
                                setPadding(8, 8, 8, 8)
                                0.5f
                            } else {
                                setPadding(0, 0, 0, 0)
                                1f
                            }

                            setOnClickListener {
                                innerChosenBitmap = bitmaps[index]
                                setPadding(8, 8, 8, 8)
                                notifyDataSetChanged()
                            }
                        } else {
                            visibility = View.GONE
                        }
                    }
                }
            }

            Node(viewHolder.rowIconSlot1, indexAdapter)
            Node(viewHolder.rowIconSlot2, indexAdapter + 1)
            Node(viewHolder.rowIconSlot3, indexAdapter + 2)
            Node(viewHolder.rowIconSlot4, indexAdapter + 3)
            Node(viewHolder.rowIconSlot5, indexAdapter + 4)
        }
    }

    class DialogNPCPicker(val bitmaps: Map<String, Bitmap>, chosenId: String) :
            RecyclerView.Adapter<DialogNPCPicker.CategoryViewHolder>() {

        var innerChosenNPC: String = chosenId
        var bitmapList = bitmaps.keys.toList()
        var inflater: View? = null

        class CategoryViewHolder(
                val rowSlot1: ImageView,
                val rowSlot2: ImageView,
                val rowSlot3: ImageView,
                val rowSlot4: ImageView,
                val rowSlot5: ImageView,
                inflater: View,
                val viewGroup: ViewGroup
        ) : RecyclerView.ViewHolder(inflater)

        override fun getItemCount() = bitmapList.size / 5 + 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            inflater = LayoutInflater.from(parent.context).inflate(R.layout.row_5_characters, parent, false)
            return CategoryViewHolder(
                    inflater!!.rowCharacterSlot1,
                    inflater!!.rowCharacterSlot2,
                    inflater!!.rowCharacterSlot3,
                    inflater!!.rowCharacterSlot4,
                    inflater!!.rowCharacterSlot5,
                    inflater
                            ?: LayoutInflater.from(parent.context).inflate(R.layout.row_5_characters, parent, false),
                    parent
            )
        }

        override fun onViewRecycled(holder: CategoryViewHolder) {
            super.onViewRecycled(holder)
            holder.rowSlot1.setImageResource(0)
            holder.rowSlot2.setImageResource(0)
            holder.rowSlot3.setImageResource(0)
            holder.rowSlot4.setImageResource(0)
            holder.rowSlot5.setImageResource(0)
        }

        override fun onBindViewHolder(viewHolder: CategoryViewHolder, position: Int) {
            val indexAdapter: Int = if (position == 0) 0 else {
                position * 5
            }

            class Node(
                    image: ImageView,
                    index: Int
            ) {
                init {
                    image.apply {
                        if (index < bitmapList.size) {
                            setImageBitmap(bitmaps[bitmapList[index]])
                            alpha = if (innerChosenNPC == bitmapList[index]) {
                                setPadding(8, 8, 8, 8)
                                0.5f
                            } else {
                                setPadding(0, 0, 0, 0)
                                1f
                            }

                            setOnClickListener {
                                innerChosenNPC = bitmapList[index]
                                setPadding(8, 8, 8, 8)
                                notifyDataSetChanged()
                            }
                        } else {
                            visibility = View.GONE
                        }
                    }
                }
            }

            Node(viewHolder.rowSlot1, indexAdapter)
            Node(viewHolder.rowSlot2, indexAdapter + 1)
            Node(viewHolder.rowSlot3, indexAdapter + 2)
            Node(viewHolder.rowSlot4, indexAdapter + 3)
            Node(viewHolder.rowSlot5, indexAdapter + 4)
        }
    }

    fun attachRecyclerPopUp(activity: GameActivity, anchorCoordinates: Coordinates): RecyclerView {
        val parent = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)

        parent.removeView(parent.findViewWithTag("attachRecyclerPopUp"))
        val recycler = RecyclerView(activity)
        recycler.apply {
            isVerticalFadingEdgeEnabled = true
            layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
            if (anchorCoordinates.y >= activity.dm.heightPixels * 0.5) {
                layoutParams.height = activity.dm.heightPixels
                y = 0f
            } else {
                layoutParams.height = (activity.dm.heightPixels - anchorCoordinates.y).toInt()
                y = anchorCoordinates.y
            }
            x = anchorCoordinates.x
            isFocusable = true
            isClickable = true
            layoutParams.width = (activity.dm.widthPixels * 0.20).toInt()
            tag = "attachRecyclerPopUp"
        }

        parent.addView(recycler)
        return recycler
    }

    fun calculateRotatedCoordinates(originalCoords: Coordinates, centerCoords: Coordinates, rotationAngle: Float): Coordinates {
        val tempX = originalCoords.x - centerCoords.x
        val tempY = originalCoords.y - centerCoords.y

        return Coordinates(((tempX * cos(Math.toRadians(rotationAngle.toDouble())) - tempY * sin(Math.toRadians(rotationAngle.toDouble()))).toFloat()), ((tempX * sin(Math.toRadians(rotationAngle.toDouble())) + tempY * cos(Math.toRadians(rotationAngle.toDouble()))).toFloat()))
    }

    fun getPointRotated(coords: Coordinates, rotationAngle: Float, centerCoords: Coordinates): Coordinates {
        // Xos, Yos // the coordinates of your center point of rect
        // R      // the angle you wish to rotate

        //The rotated position of this corner in world coordinates
        val rotatedX = coords.x + (centerCoords.x * cos(Math.toRadians(rotationAngle.toDouble()))) - (centerCoords.y * sin(Math.toRadians(rotationAngle.toDouble())))
        val rotatedY = coords.y + (centerCoords.x * sin(Math.toRadians(rotationAngle.toDouble()))) + (centerCoords.y * cos(Math.toRadians(rotationAngle.toDouble())))

        return Coordinates(rotatedX.toFloat(), rotatedY.toFloat())
    }

    enum class FrameworkComponentType : Serializable {
        MONOLOG,
        DIALOG,
        IMAGE,
        TEXT
    }

    enum class FrameworkComponentLocker: Serializable {
        WIDTH,
        HEIGHT,
        NONE
    }

    data class FrameworkComponentTemplate(
            var title: String = "Image",
            var description: String = "Images from game library",
            var type: FrameworkComponentType = FrameworkComponentType.IMAGE,
            var bitmapsId: MutableList<String> = mutableListOf(),
            var bitmapIconId: String = ""
    ) {
        @Exclude @Transient var bitmaps: MutableList<Bitmap?> = mutableListOf()
            get() {
                field.clear()
                for (i in bitmapsId) {
                    field.add(Data.downloadedBitmaps[i])
                }
                return field
            }
        @Exclude @Transient var bitmapIcon: Bitmap? = null
            get() {
                return Data.downloadedBitmaps[bitmapIconId]
            }
    }

    data class FrameworkComponent(
            var type: FrameworkComponentType = FrameworkComponentType.IMAGE,
            var coordinates: Coordinates = Coordinates(0f, 0f),             //in percentage, max 100
            var width: Float = 60.0f,             //in percentage, max 100
            var height: Float = 60.0f,             //in percentage, max 100
            var bitmapId: String = "",
            var rotationAngle: Float = 0f,             //in degrees, max 360
            var name: String = "",
            var textContent: String = "enter text here",
            var animate: Boolean = false,        //not fully decided yet, animated appearance of text is an option
            var fontSizeType: CustomTextView.SizeType = CustomTextView.SizeType.adaptive,
            var textColor: String = "black",
            var hasBackground: Boolean = false,
            var backgroundColor: String = "white",
            var innerIndex: Int = 0
    ) : Serializable {
        var innerId: String = UUID.randomUUID().toString()
        var lockedEditor: Boolean = false

        @Exclude
        fun copy(): FrameworkComponent {
            return FrameworkComponent(
                    type = this.type,
                    coordinates = Coordinates(this.coordinates.x, this.coordinates.y),
                    width = this.width,
                    height = this.height,
                    bitmapId = this.bitmapId,
                    rotationAngle = this.rotationAngle,
                    name = this.name,
                    textContent = this.textContent,
                    animate = this.animate,
                    fontSizeType = this.fontSizeType,
                    textColor = this.textColor,
                    innerIndex = this.innerIndex
            )
        }

        //call calculateRealMetrics() to refresh the values
        @Exclude var lockedOn: FrameworkComponentLocker = FrameworkComponentLocker.NONE
        @Exclude @Transient var realWidth: Int = 0
            @Exclude get
        @Exclude @Transient var realHeight: Int = 0
            @Exclude get
        @Exclude @Transient var realCoordinates = Coordinates(0f, 0f)
            @Exclude get
        @Exclude @Transient var absoluteCoordinates = Coordinates(0f, 0f)
            @Exclude get(){
                val coords = intArrayOf(0, 0)
                this.view?.getLocationOnScreen(coords)
                return Coordinates(coords[0].toFloat(), coords[1].toFloat())
            }
        @Exclude @Transient var view: View? = null
            @Exclude get
        @Exclude @Transient var created: Boolean = false
            @Exclude get
        @Exclude @Transient var bitmap: Bitmap? = null
            @Exclude get() {
                return Data.downloadedBitmaps[bitmapId]
            }

        @Exclude @Transient var startDragHandler = Handler()
            @Exclude get
        @Exclude @Transient var validClickHandler = Handler()
            @Exclude get

        init {
            realCoordinates = Coordinates(0f, 0f)
            realHeight = 0
            realWidth = 0
        }

        fun getMetricsFromReal(activity: GameActivity) {
            val layoutWidth = ((activity as? Activity_Create_Story)?.layoutWidth ?: currentGameActivity?.dm?.widthPixels ?: 1).toDouble()
            val layoutHeight = ((activity as? Activity_Create_Story)?.layoutHeight ?: currentGameActivity?.dm?.heightPixels ?: 1).toDouble()
            width = (realWidth.toDouble() / layoutWidth * 100.0).toFloat()
            height = (realHeight.toDouble() / layoutHeight * 100.0).toFloat()
        }

        fun getCoordinatesFromReal(activity: GameActivity) {
            val layoutWidth = (activity as? Activity_Create_Story)?.layoutWidth ?: currentGameActivity?.dm?.widthPixels ?: 1
            val layoutHeight = (activity as? Activity_Create_Story)?.layoutHeight ?: currentGameActivity?.dm?.heightPixels ?: 1
            coordinates.x = ((realCoordinates.x / layoutWidth * 100.0).toFloat())
            coordinates.y = ((realCoordinates.y / layoutHeight * 100.0).toFloat())
        }

        fun resolveSizeByDrawable(activity: GameActivity) {
            val layoutWidth = (activity as? Activity_Create_Story)?.layoutWidth ?: currentGameActivity?.dm?.widthPixels ?: 1
            val layoutHeight = (activity as? Activity_Create_Story)?.layoutHeight ?: currentGameActivity?.dm?.heightPixels ?: 1

            val heightRatio = (bitmap?.height?.toDouble()
                    ?: 1.0) / (bitmap?.width?.toDouble()
                    ?: 1.0)
            val heightMetricsRatio = layoutWidth.toDouble() / layoutHeight.toDouble()

            val widthRatio = (bitmap?.width?.toDouble()
                    ?: 1.0) / (bitmap?.height?.toDouble()
                    ?: 1.0)
            val widthMetricsRatio = layoutHeight.toDouble() / layoutWidth.toDouble()

            if (heightRatio < 1) {
                this.height = (this.width * heightRatio * heightMetricsRatio).toFloat()
            } else {
                this.width = (this.height * widthRatio * widthMetricsRatio).toFloat()
            }
            calculateRealMetrics(activity)
        }

        fun calculateRealMetrics(activity: GameActivity) {
            val layoutWidth = ((activity as? Activity_Create_Story)?.layoutWidth ?: currentGameActivity?.dm?.widthPixels ?: 1).toDouble()
            val layoutHeight = ((activity as? Activity_Create_Story)?.layoutHeight ?: currentGameActivity?.dm?.heightPixels ?: 1).toDouble()
            //realCoordinates = Coordinates(0f, 0f)
            val heightRatio = (bitmap?.height?.toDouble()
                    ?: 1.0) / (bitmap?.width?.toDouble()
                    ?: 1.0)
            val heightMetricsRatio = layoutWidth / layoutHeight

            val widthRatio = (bitmap?.width?.toDouble()
                    ?: 1.0) / (bitmap?.height?.toDouble()
                    ?: 1.0)
            val widthMetricsRatio = layoutHeight / layoutWidth

            if(lockedOn == FrameworkComponentLocker.WIDTH){
                height = (width * heightRatio * heightMetricsRatio).toFloat()
            }else if(lockedOn == FrameworkComponentLocker.HEIGHT){
                width = (height * widthRatio * widthMetricsRatio).toFloat()
            }

            calculateMetrics(layoutWidth, layoutHeight)
            calculateCoordinates(layoutWidth, layoutHeight)
        }

        private fun calculateMetrics(layoutWidth: Double, layoutHeight: Double){
            realWidth = 0
            realHeight = 0
            width = max(1f, min(100 - coordinates.x, width))
            height = max(1f, min(100 - coordinates.y, height))

            realWidth = min((1 - coordinates.x / 100) * layoutWidth, (layoutWidth * (width.toDouble() / 100.0))).toInt()
            realHeight = min((1 - coordinates.y / 100) * layoutHeight, (layoutHeight * (height.toDouble() / 100.0))).toInt()
        }

        private fun calculateCoordinates(layoutWidth: Double, layoutHeight: Double){
            realCoordinates = Coordinates(0f, 0f)
            coordinates.x = max(0f, min((layoutWidth - realWidth).toFloat(), coordinates.x))
            coordinates.y = max(0f, min((layoutHeight - realHeight).toFloat(), coordinates.y))

            realCoordinates.x = (max(0.0, min((layoutWidth - realWidth), layoutWidth * coordinates.x.toDouble() / 100))).toFloat()
            realCoordinates.y = (max(0.0, min((layoutHeight - realHeight), layoutHeight * coordinates.y.toDouble() / 100))).toFloat()
        }

        fun findMyView(activity: GameActivity, container: ViewGroup? = null): View? {
            val parent = container ?: activity.window.decorView.findViewById(android.R.id.content)

            view = parent.findViewWithTag(this.innerId)
            try {

            }catch (e: Exception){ Log.d("Framework_findMyView", "failed at findViewWithTag") }

            return view
        }

        fun update(activity: GameActivity, viewOnly: Boolean = false, container: ViewGroup? = null) {
            calculateRealMetrics(activity)

            findMyView(activity, container)

            if (view == null) {
                created = true
                createView(activity, viewOnly, container)
            } else {
                view?.apply {
                    (this as? CustomTextView)?.apply {
                        if(viewOnly) animateText(textContent) else setHTMLText(textContent)
                        fontSizeType = this@FrameworkComponent.fontSizeType
                        setTextColor(Data.colorNameMap.getOrElse(this@FrameworkComponent.textColor) {this@FrameworkComponent.textColor.toIntOrNull() ?: 0})
                        when {
                            this@FrameworkComponent.hasBackground -> {
                                setPadding(4, 4, 4, 4)
                                setBackgroundColor(Data.colorNameMap.getOrElse(this@FrameworkComponent.backgroundColor){ this@FrameworkComponent.backgroundColor.toIntOrNull() ?: 0})
                            }
                            (activity as? Activity_Create_Story)?.currentComponentID == this@FrameworkComponent.innerId -> {
                                setPadding(8, 8, 8, 8)
                                setBackgroundResource(R.drawable.framework_frame_white)
                            }
                            else -> {
                                setPadding(0, 0, 0, 0)
                                setBackgroundResource(0)
                            }
                        }
                    }
                    layoutParams?.width = realWidth
                    layoutParams?.height = realHeight
                    rotation = rotationAngle
                    x = realCoordinates.x
                    y = realCoordinates.y
                    pivotX = (realWidth / 2).toFloat()
                    pivotY = (realHeight / 2).toFloat()
                }
                view?.invalidate()
                view?.requestLayout()
            }
        }

        private fun spacing(event: MotionEvent): Float {
            val x = event.getX(0) - event.getX(1)
            val y = event.getY(0) - event.getY(1)
            val s = x * x + y * y
            return sqrt(s)
        }

        private fun spacingX(event: MotionEvent): Float {
            val x = event.getX(0) - event.getX(1)
            val s = x * x
            return sqrt(s)
        }

        private fun spacingY(event: MotionEvent): Float {
            val y = event.getY(0) - event.getY(1)
            val s = y * y
            return sqrt(s)
        }

        private fun midPoint(point: PointF, event: MotionEvent) {
            val x = event.getX(0) + event.getX(1)
            val y = event.getY(0) + event.getY(1)
            point.set(x / 2, y / 2)
        }

        private fun rotation(event: MotionEvent): Float {
            val deltaX = (event.getX(0) - event.getX(1))
            val deltaY = (event.getY(0) - event.getY(1))
            val radians = atan2(deltaY, deltaX).toDouble()
            return Math.toDegrees(radians).toFloat()
        }

        fun createView(activity: GameActivity, viewOnly: Boolean = false, container: ViewGroup? = null) {
            if(!viewOnly){
                startDragHandler = Handler()
                validClickHandler = Handler()
            }

            //var maximized = (activity as? Activity_Create_Story)?.maximized ?: false
            val layoutWidth = (activity as? Activity_Create_Story)?.layoutWidth ?: currentGameActivity?.dm?.widthPixels ?: 1
            val layoutHeight = (activity as? Activity_Create_Story)?.layoutHeight ?: currentGameActivity?.dm?.heightPixels ?: 1

            if (created || viewOnly) {
                calculateRealMetrics(activity)
            } else {
                resolveSizeByDrawable(activity)
            }

            val CODE_NONE = 0
            val CODE_DRAG = 1
            val CODE_ZOOM = 2
            val CODE_ZOOMX = 3
            val CODE_ZOOMY = 4
            val CODE_ZOOMXY = 5

            val CODE_ONE_POINTER_LEFT = 101
            val CODE_ONE_POINTER_RIGHT = 102
            val CODE_ONE_POINTER_TOP = 103
            val CODE_ONE_POINTER_BOTTOM = 104

            var zoomMode = CODE_NONE
            val matrix = Matrix()
            val savedMatrix = Matrix()
            var currentMode = CODE_DRAG
            val start = PointF()
            val mid = PointF()
            var oldDistance = 1f
            var oldDistanceX = 1f
            var oldDistanceY = 1f
            var d = 0f
            var newRotation: Float
            var lastEvent: Array<Float>? = null
            var bitmap: Bitmap
            val r = 0f
            var scale = 1f
            var scaleX = 1f
            var scaleY = 1f
            var originalWidth = this@FrameworkComponent.width
            var originalHeight = this@FrameworkComponent.height
            var originalCoordX = this.coordinates.x.absoluteValue
            var originalCoordY = this.coordinates.y.absoluteValue
            var lastRealWidth = realWidth
            var lastRealHeight = realHeight
            var validClick = false
            var pointerEvent = false
            var onePointerEvent = false

            val backgroundHandler = Handler()

            var initialX = 0f
            var initialY = 0f

            view = when (this.type) {
                FrameworkComponentType.IMAGE -> {
                    ImageView(activity)
                }
                FrameworkComponentType.TEXT -> {
                    CustomTextView(activity)
                }
                else -> {
                    ImageView(activity)
                }
            }
            view?.apply {
                layoutParams = ConstraintLayout.LayoutParams(ConstraintLayout.LayoutParams.MATCH_CONSTRAINT, ConstraintLayout.LayoutParams.MATCH_CONSTRAINT)
                layoutParams.width = realWidth
                layoutParams.height = realHeight
                //layout(0, 0, realWidth, realHeight)
                x = realCoordinates.x
                y = realCoordinates.y
                tag = innerId
                rotation = rotationAngle
                isClickable = true
                isEnabled = true
                z = 0f
                elevation = 0f
                (this as? ImageView)?.setImageBitmap(this@FrameworkComponent.bitmap)
                (this as? ImageView)?.scaleType = ImageView.ScaleType.FIT_XY
                (this as? CustomTextView)?.apply {
                    setHTMLText(textContent)
                    fontSizeType = this@FrameworkComponent.fontSizeType
                    setTextColor(Data.colorNameMap.getOrElse(this@FrameworkComponent.textColor){this@FrameworkComponent.textColor.toIntOrNull() ?: 0})
                }
                when {
                    this@FrameworkComponent.hasBackground -> {
                        setPadding(4, 4, 4, 4)
                        setBackgroundColor(Data.colorNameMap.getOrElse(this@FrameworkComponent.backgroundColor){this@FrameworkComponent.backgroundColor.toIntOrNull() ?: 0})
                    }
                    (activity as? Activity_Create_Story)?.currentComponentID == this@FrameworkComponent.innerId -> {
                        setPadding(8, 8, 8, 8)
                        setBackgroundResource(R.drawable.framework_frame_white)
                    }
                    else -> {
                        setPadding(0, 0, 0, 0)
                        setBackgroundResource(0)
                    }
                }

                if(!viewOnly){
                    setOnTouchListener { _, event ->
                        if ((activity as? Activity_Create_Story)?.draggedComponent?.innerId == this@FrameworkComponent.innerId || lockedEditor) {
                            (activity as? Activity_Create_Story)?.removeCurrentPropertiesOptions()
                            return@setOnTouchListener false
                        }

                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                pointerEvent = false
                                onePointerEvent = false
                                zoomMode = CODE_NONE
                                currentMode = CODE_DRAG
                                oldDistance = 1f
                                oldDistanceX = 1f
                                oldDistanceY = 1f
                                scale = 1f
                                scaleX = 1f
                                scaleY = 1f
                                originalWidth = this@FrameworkComponent.width
                                originalHeight = this@FrameworkComponent.height
                                originalCoordX = this@FrameworkComponent.coordinates.x
                                originalCoordY = this@FrameworkComponent.coordinates.y

                                initialX = event.rawX
                                initialY = event.rawY

                                validClick = true
                                validClickHandler.removeCallbacksAndMessages(null)
                                validClickHandler.postDelayed({
                                    validClick = false
                                }, 100)

                                startDragHandler.removeCallbacksAndMessages(null)
                                startDragHandler.postDelayed({
                                    val validDragRect = RectF(
                                            (absoluteCoordinates.x + realWidth * 0.2).toFloat(),
                                            (absoluteCoordinates.y + realHeight * 0.2).toFloat(),
                                            (absoluteCoordinates.x + realWidth * 0.8).toFloat(),
                                            (absoluteCoordinates.y + realHeight * 0.8).toFloat()
                                    )
                                    if (currentMode == CODE_DRAG && event.pointerCount == 1 && validDragRect.contains(initialX, initialY)) {         //long click with limitations
                                        findMyView(activity, container)

                                        val item = ClipData.Item(bitmapId)
                                        val dragData = ClipData(
                                                "storyComponent",
                                                arrayOf(bitmapId),
                                                item)
                                        calculateRealMetrics(activity)
                                        (activity as? Activity_Create_Story)?.draggedComponent = this@FrameworkComponent
                                        (activity as? Activity_Create_Story)?.removeCurrentPropertiesOptions(removeMarkers = false, reIndex = false)
                                        vibrateAsError(activity)

                                        val myShadow = StoryDragListener(view, realWidth, realHeight, rotationAngle, activity.getDrawable(R.color.bg_basic_white))
                                        view?.startDrag(
                                                dragData,   // the data to be dragged
                                                myShadow,   // the drag shadow builder
                                                null,       // no need to use local data
                                                0           // flags (not currently used, set to 0)
                                        )
                                    }
                                }, 600)
                            }

                            MotionEvent.ACTION_POINTER_DOWN -> {
                                pointerEvent = true
                                oldDistance = spacing(event)
                                Log.d("spacing", spacing(event).toString())
                                oldDistanceX = spacingX(event)
                                oldDistanceY = spacingY(event)
                                if (oldDistance > max(lastRealWidth, lastRealHeight) * 0.05) {
                                    savedMatrix.set(matrix)
                                    midPoint(mid, event)
                                    currentMode = CODE_ZOOM
                                }
                                lastEvent = arrayOf(event.getX(0), event.getX(1), event.getY(0), event.getY(1))
                                d = rotation(event)
                            }
                            MotionEvent.ACTION_UP -> {
                                if (validClick && this.isClickable) {         //click performed
                                    (activity as? Activity_Create_Story)?.apply {
                                        addPropertiesOptions(this@FrameworkComponent)
                                        relayoutIndexing()
                                    }
                                    isClickable = false
                                    Handler().postDelayed({
                                        this.isClickable = true
                                    }, 200)

                                    view?.bringToFront()
                                    if ((activity as? Activity_Create_Story)?.showBoundaries == true) {
                                        (view as? CustomTextView)?.setBackgroundResource(R.drawable.framework_frame_white)
                                        (view as? ImageView)?.apply {
                                            setPadding(8, 8, 8, 8)
                                            setBackgroundResource(R.drawable.framework_frame_white)
                                        }
                                        /*backgroundHandler.removeCallbacksAndMessages(null)
                                        backgroundHandler.postDelayed({
                                            view?.setBackgroundResource(0)
                                        }, 1500)*/
                                    }
                                }
                                currentMode = CODE_NONE
                            }
                            MotionEvent.ACTION_POINTER_UP -> {
                                currentMode = CODE_NONE
                                lastEvent = null
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if ((activity as? Activity_Create_Story)?.currentComponentID != this@FrameworkComponent.innerId) return@setOnTouchListener false

                                if (event.pointerCount > 1) {
                                    when (currentMode) {
                                        CODE_ZOOM -> {
                                            val newDistance = spacing(event)
                                            val newDistanceX = spacingX(event)
                                            val newDistanceY = spacingY(event)

                                            val xyRatio = newDistanceX * (realHeight.toDouble() / realWidth.toDouble() * layoutHeight.toDouble() / layoutWidth.toDouble()) / newDistanceY
                                            if (newDistance > max(lastRealWidth, lastRealHeight) * 0.05) {
                                                //matrix.set(savedMatrix)
                                                scale = newDistance / oldDistance
                                                scaleX = newDistanceX / oldDistanceX
                                                scaleY = newDistanceY / oldDistanceY
                                                //matrix.postScale(scale, scale, mid.x, mid.y)
                                                if (zoomMode == CODE_NONE) {
                                                    zoomMode = when {
                                                        abs(1.0 - xyRatio) < 0.7 -> {
                                                            CODE_ZOOMXY
                                                        }
                                                        newDistanceX > newDistanceY -> {
                                                            CODE_ZOOMX
                                                        }
                                                        else -> CODE_ZOOMY
                                                    }
                                                }
                                            }
                                            /*if(lastEvent != null && event.pointerCount == 2 || event.pointerCount == 3){  ROTATION
                                                newRotation = rotation(event)
                                                r = newRotation - d
                                                val values = FloatArray(9)
                                                matrix.getValues(values)
                                                val tx = values[2]
                                                val ty = values[5]
                                                val sx = values[0]
                                                val xc = realWidth / 2 * sx
                                                val yc = realHeight / 2 * sx
                                                matrix.postRotate(r, tx + xc, ty + yc)
                                            }*/
                                        }
                                    }

                                } else {
                                    //maximized = (activity as? Activity_Create_Story)?.maximized ?: false

                                    val distanceX = event.rawX - initialX
                                    val distanceY = event.rawY - initialY
                                    val additionalPercX = (distanceX.toDouble() / (layoutWidth.toDouble()) * 100)
                                    val additionalPercY = ((distanceY.toDouble() / layoutHeight.toDouble()) * 100)

                                    if (currentMode > 100) {      //above code 100 identify one pointer events only      //TODO corner mode + its lifecycle
                                        when (currentMode) {
                                            CODE_ONE_POINTER_LEFT -> {
                                                this@FrameworkComponent.width = if(additionalPercX < 0 && coordinates.x.toInt() == 0){
                                                    this@FrameworkComponent.width
                                                }else (originalWidth - additionalPercX).toFloat()
                                                coordinates.x = (originalCoordX + additionalPercX).toFloat()

                                                calculateRealMetrics(activity)
                                            }
                                            CODE_ONE_POINTER_RIGHT -> {
                                                this@FrameworkComponent.width = (originalWidth + additionalPercX).toFloat()
                                            }
                                            CODE_ONE_POINTER_TOP -> {
                                                this@FrameworkComponent.height = if(additionalPercY < 0 && coordinates.y.toInt() == 0){
                                                    this@FrameworkComponent.height
                                                }else (originalHeight.toDouble() - additionalPercY).toFloat()
                                                coordinates.y = (originalCoordY + additionalPercY).toFloat()

                                                calculateRealMetrics(activity)
                                            }
                                            CODE_ONE_POINTER_BOTTOM -> {
                                                this@FrameworkComponent.height = (originalHeight.toDouble() + additionalPercY).toFloat()
                                            }
                                        }
                                    } else {
                                        this@FrameworkComponent.calculateRealMetrics(activity)

                                        val leftRect = Rectangle()
                                        leftRect.setBounds(absoluteCoordinates.x.toInt(), absoluteCoordinates.y.toInt(), (realWidth.toDouble() * 0.25).toInt(), realHeight)

                                        val rightRect = Rectangle()
                                        rightRect.setBounds((absoluteCoordinates.x + realWidth.toDouble() * 0.75).toInt(), absoluteCoordinates.y.toInt(), (realWidth.toDouble() * 0.25).toInt(), realHeight)

                                        val topRect = Rectangle()
                                        topRect.setBounds(absoluteCoordinates.x.toInt(), absoluteCoordinates.y.toInt(), realWidth, (realHeight.toDouble() * 0.25).toInt())

                                        val bottomRect = Rectangle()
                                        bottomRect.setBounds(absoluteCoordinates.x.toInt(), (absoluteCoordinates.y + realHeight.toDouble() * 0.75).toInt(), realWidth, (realHeight.toDouble() * 0.25).toInt())

                                        when {
                                            leftRect.contains(event.rawX.toInt(), event.rawY.toInt()) -> {
                                                if (abs(distanceX) > 10f && abs(distanceX) > abs(distanceY)) {
                                                    onePointerEvent = true
                                                    currentMode = CODE_ONE_POINTER_LEFT
                                                }
                                            }
                                            rightRect.contains(event.rawX.toInt(), event.rawY.toInt()) -> {
                                                if (abs(distanceX) > 10f && abs(distanceX) > abs(distanceY)) {
                                                    onePointerEvent = true
                                                    currentMode = CODE_ONE_POINTER_RIGHT
                                                }
                                            }
                                            topRect.contains(event.rawX.toInt(), event.rawY.toInt()) -> {
                                                if (abs(distanceY) > 10f && abs(distanceY) > abs(distanceX)) {
                                                    onePointerEvent = true
                                                    currentMode = CODE_ONE_POINTER_TOP
                                                }
                                            }
                                            bottomRect.contains(event.rawX.toInt(), event.rawY.toInt()) -> {
                                                if (abs(distanceY) > 10f && abs(distanceY) > abs(distanceX)) {
                                                    onePointerEvent = true
                                                    currentMode = CODE_ONE_POINTER_BOTTOM
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        /*(view as? ImageView)?.imageMatrix = matrix
                        bitmap = Bitmap.createBitmap(realWidth, realHeight, Bitmap.Config.RGB_565)*/

                        if ((activity as? Activity_Create_Story)?.currentComponentID == this@FrameworkComponent.innerId) {
                            if (pointerEvent) {
                                //maximized = (activity as? Activity_Create_Story)?.maximized ?: false
                                with(this@FrameworkComponent) {
                                    //Log.d("pointerEvent=true", "before update, width: $width, height: $height, scaleX: $scaleX, scaleY: $scaleY, zoomMode: $zoomMode, originalWidth: $originalWidth, originalHeight: $originalHeight")
                                    width = min(100f, (originalWidth.toDouble() * when (zoomMode) {
                                        CODE_ZOOMX -> scaleX
                                        CODE_ZOOMXY -> scale
                                        else -> 1f
                                    }).toFloat())
                                    height = min(100f, (originalHeight.toDouble() * when (zoomMode) {
                                        CODE_ZOOMY -> scaleY
                                        CODE_ZOOMXY -> scale
                                        else -> 1f
                                    }).toFloat())
                                    //rotationAngle = min(360f, r)
                                    calculateRealMetrics(activity)
                                    realCoordinates.x += (lastRealWidth - realWidth) / 2
                                    realCoordinates.y += (lastRealHeight - realHeight) / 2
                                    getCoordinatesFromReal(activity)
                                    update(activity = activity, container = container)

                                    (activity as? Activity_Create_Story)?.removeCurrentPropertiesOptions(removeMarkers = false, reIndex = false)
                                    /*if((activity as? Activity_Create_Story)?.showBoundaries == true){
                                        (view as? CustomTextView)?.setBackgroundColor(Color.WHITE)
                                        backgroundHandler.removeCallbacksAndMessages(null)
                                        backgroundHandler.postDelayed({
                                            view?.setBackgroundResource(0)
                                        }, 400)
                                    }*/
                                }

                                lastRealWidth = realWidth
                                lastRealHeight = realHeight
                            } else if (onePointerEvent) {
                                update(activity = activity, container = container)
                                lastRealWidth = realWidth
                                lastRealHeight = realHeight

                                (activity as? Activity_Create_Story)?.removeCurrentPropertiesOptions(removeMarkers = false, reIndex = false)
                                /*if((activity as? Activity_Create_Story)?.showBoundaries == true){
                                    (view as? CustomTextView)?.setBackgroundColor(Color.WHITE)
                                    backgroundHandler.removeCallbacksAndMessages(null)
                                    backgroundHandler.postDelayed({
                                        view?.setBackgroundResource(0)
                                    }, 400)
                                }*/
                            }
                            (activity as? Activity_Create_Story)?.apply {
                                textViewCreateStoryName?.setHTMLText("<b>width: ${this@FrameworkComponent.width.toInt()}%, height: ${this@FrameworkComponent.height.toInt()}%</b>")

                                if(imageViewCreateStoryMaximize?.visibility != View.GONE){
                                    textViewCreateStoryName?.visibility = View.VISIBLE
                                    imageViewCreateStoryMaximize?.visibility = View.GONE
                                }

                                slideNameHandler.removeCallbacksAndMessages(null)
                                slideNameHandler.postDelayed({
                                    imageViewCreateStoryMaximize?.visibility = View.VISIBLE
                                    textViewCreateStoryName?.visibility = View.GONE
                                }, 1000)
                            }
                        }

                        /*val canvas = Canvas(bitmap)
                        canvas.save()
                        canvas.translate(coordinates.x, coordinates.y)
                        view?.draw(canvas)
                        canvas.restore()*/
                        true
                    }
                }
            }
            (activity as? Activity_Create_Story)?.attachComponent(view ?: View(activity))
            if(viewOnly) container?.addView(view ?: View(activity))
            created = true
        }
    }

    class BackgroundSoundService : Service() {

        override fun onBind(arg0: Intent): IBinder? {
            return null
        }

        override fun onCreate() {
            super.onCreate()
            Data.mediaPlayer = MediaPlayer.create(this, if(nextInt(0, 1) == 0) Data.bgm1 else Data.bgm2)
            Data.mediaPlayer?.isLooping = true                                            // Set looping
            Data.mediaPlayer?.setVolume(100f, 100f)

            Data.mediaPlayer?.setOnCompletionListener {
                Data.mediaPlayer?.release()
            }
        }

        override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
            Data.mediaPlayer?.start()
            return START_NOT_STICKY
        }

        override fun onStart(intent: Intent, startId: Int) {
        }

        fun pause() {
            Data.mediaPlayer?.stop()
            Data.mediaPlayer?.release()
            Data.mediaPlayer = null
        }

        override fun onDestroy() {
            Data.mediaPlayer?.stop()
            Data.mediaPlayer?.release()
        }

        override fun onLowMemory() {
        }
    }

    class LifecycleListener(val context: Context) : LifecycleObserver {

        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        fun onMoveToForeground() {
            context.stopService(Intent(context, ClassCubeItHeadService::class.java))
            Data.player.syncStats()
            if (Data.player.music && Data.player.username != "player") {
                val svc = Intent(context, Data.bgMusic::class.java)
                Handler().postDelayed({
                    context.startService(svc)
                }, 500)
            }
            Data.player.online = true
            Data.player.uploadSpecifiedItems(mapOf("online" to true))
            //if (Data.player.currentStoryQuest != null && Data.player.currentStoryQuest!!.progress == 0) Data.player.currentStoryQuest = null
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
        fun onMoveToBackground() {
            if (Data.player.music && Data.mediaPlayer != null) {
                val svc = Intent(context, Data.bgMusic::class.java)
                context.stopService(svc)
            }
            Data.player.online = false
            Data.player.uploadPlayer()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Data.player.appearOnTop) {
                if (Settings.canDrawOverlays(context)) {
                    context.startService(Intent(context, ClassCubeItHeadService::class.java))
                }
            }
        }
    }

    fun showNotification(titleInput: String, textInput: String, context: Context): androidx.appcompat.app.AlertDialog {
        Looper.prepare()
        val builder = androidx.appcompat.app.AlertDialog.Builder(context)
        builder.setTitle(titleInput)
        builder.setMessage(textInput)
        val dialog: androidx.appcompat.app.AlertDialog = builder.create()
        dialog.show()
        return dialog
    }

    fun isStoragePermissionGranted(activity: Activity): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v("WRITE_EXTERNAL_STORAGE", "Permission is granted")
                true
            } else {
                Log.v("WRITE_EXTERNAL_STORAGE", "Permission is revoked")
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
                false
            }
        } else { //permission is automatically granted on sdk<23 upon installation
            Log.v("WRITE_EXTERNAL_STORAGE", "Permission is granted")
            true
        }
    }

    fun screenShot(view: View): Bitmap {
        System.gc()
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }

    fun bitmapToURI(context: Context, inImage: Bitmap, title: String, description: String?): Uri {
        val bytes = ByteArrayOutputStream()
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
        val path = MediaStore.Images.Media.insertImage(context.contentResolver, inImage, title, description)
        return Uri.parse(path ?: "")
    }

    @Throws(IOException::class)
    fun writeObject(context: Context, fileName: String, objectG: Any) {
        context.openFileOutput(fileName, Context.MODE_PRIVATE).close()

        val fos = context.openFileOutput(fileName, Context.MODE_PRIVATE)
        val oos = ObjectOutputStream(fos)
        oos.reset()
        try {
            oos.writeObject(objectG)
        }catch (e1: NotSerializableException){
            Log.e("WriteObject returned: ", e1.localizedMessage)
        }
        oos.flush()
        oos.close()
        fos.close()
    }

    fun removeObject(context: Context, fileName: String): Boolean{
        val file = context.getFileStreamPath(fileName)
        return if(file.exists()){
            file.delete()
        }else false
    }


    @Throws(IOException::class, ClassNotFoundException::class)
    fun readObject(context: Context, fileName: String): Any {
        val file = context.getFileStreamPath(fileName)
        if (!file.exists()) {
            file.createNewFile()
        }
        val fis = context.openFileInput(fileName)
        return if (file.readText() != "") {
            val ois = ObjectInputStream(fis)
            try {
                ois.readObject()
            } catch (e1: NotSerializableException) {
                return 0
            } catch (e2: InvalidClassException) {
                return 0
            }
        } else {
            0
        }
    }

    fun readFileText(context: Context, fileName: String): String {
        val file = context.getFileStreamPath(fileName)
        if (!file.exists() || file.readText() == "") {
            file.createNewFile()
            file.writeText("0")
        }
        return file.readText()
    }

    fun writeFileText(context: Context, fileName: String, content: String) {
        val file = context.getFileStreamPath(fileName)
        file.delete()
        file.createNewFile()
        file.writeText(content)
    }

    fun exceptionFormatter(errorIn: String): String {

        return if (errorIn.contains("com.google.firebase.auth")) {

            val regex = Regex("com.google.firebase.auth.\\w+: ")
            errorIn.replace(regex, "Error: ")
        } else {
            Log.d("ExceptionFormatterError", "Failed to format exception, falling back to source")
            errorIn
        }
    }

    /**
     * @sample clientPostData("MyUrl.com", "content I'm about to send (JSON ideally)", object : Callback {
            override fun onFailure(call: Call, e: IOException) {
            }
            override fun onResponse(call: Call, response: Response) {
            }
            })
     * @author Jakub Kostka, Czechia
     * @since Alpha 0.5.0.4
     * @param callback handler for results
     */
    fun clientPostData(url: String, content: String, callback: Callback) {
        val okHttpClient = OkHttpClient()
        val requestBody = content.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
                .method("POST", requestBody)
                .url(url)
                .build()
        request.headers.newBuilder().add("Accept: application/json")
        okHttpClient.newCall(request).enqueue(callback)
    }
}