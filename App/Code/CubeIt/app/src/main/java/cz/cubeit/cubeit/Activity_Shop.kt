package cz.cubeit.cubeit

import android.animation.Animator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_shop.*
import kotlinx.android.synthetic.main.activity_shop.textViewShopItemInfo
import kotlinx.android.synthetic.main.popup_dialog.view.*
import kotlinx.android.synthetic.main.row_shop_inventory.view.*
import kotlinx.android.synthetic.main.row_shop_offer.view.*

class Activity_Shop : SystemFlow.GameActivity(R.layout.activity_shop, ActivityType.Shop, true, R.id.imageViewActivityShop, hasSwipeMenu = false){
    var lastClicked = ""

    override fun onBackPressed() {
        val intent = Intent(this, ActivityHome::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(intent)
        this.overridePendingTransition(0,0)
    }

    private fun refreshListViews(){
        (recyclerViewShopOffer.adapter as ShopOffer).notifyDataSetChanged()
        (recyclerViewShopInventory.adapter as ShopInventory).notifyDataSetChanged()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Data.player.syncStats()

        System.gc()
        val opts = BitmapFactory.Options()
        opts.inScaled = false
        imageViewActivityShop.setImageBitmap(BitmapFactory.decodeResource(resources, R.drawable.shop_bg, opts))

        val animDownText: Animation = AnimationUtils.loadAnimation(applicationContext,
                R.anim.animation_shop_text_down)

        textViewShopItemInfo.startAnimation(animDownText)
        val originalCoinY = imageViewShopCoin.y

        propertiesBar.attach()

        recyclerViewShopInventory.apply {
            layoutManager = LinearLayoutManager(this@Activity_Shop)
            adapter = ShopInventory(this@Activity_Shop, Data.player, textViewShopItemInfo, layoutInflater.inflate(R.layout.popup_dialog,null), recyclerViewShopInventory)
            setOnDragListener(inventoryShopDragListener)

            /*addOnScrollListener(
                    object : RecyclerView.OnScrollListener() {
                        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                            super.onScrolled(recyclerView, dx, dy)
                            if(dy > 0){
                                recyclerViewShopInventory.y = max(16f, recyclerViewShopInventory.y - abs(dy / 2))
                            }else {
                                recyclerViewShopInventory.y = min(0f, recyclerViewShopInventory.y + abs(dy / 2))
                            }
                            recyclerViewShopInventory.layoutParams.height = (dm.heightPixels - (recyclerViewShopInventory.y)).toInt()
                            recyclerViewShopInventory.requestLayout()
                        }
                    }
            )*/
        }

        recyclerViewShopOffer.apply {
            layoutManager = LinearLayoutManager(this@Activity_Shop)
            adapter = ShopOffer(textViewShopItemInfo, this@Activity_Shop)
            layoutParams.width = (dm.heightPixels * 0.87).toInt()
            setOnDragListener(shopOfferDragListener)
        }

        var animationRefresh = ValueAnimator()

        imageViewActivityShopBack.setOnClickListener {
            onBackPressed()
        }

        imageViewActivityShopCharacter.setOnClickListener {
            val intent = Intent(this, Activity_Character::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            startActivity(intent)
            this.overridePendingTransition(0,0)
        }

        shopOfferRefresh.setOnClickListener {refresh: View ->
            val moneyReq = Data.player.level * 10

            if(!animationRefresh.isRunning){
                if(Data.player.cubeCoins >= moneyReq){
                    //SystemFlow.playComponentSound(this, R.raw.basic_purchase)
                    Data.player.cubeCoins -= moneyReq
                    propertiesBar.updateProperties()
                    for(i in 0 until Data.player.shopOffer.size){
                        Data.player.shopOffer[i] = GameFlow.generateItem(Data.player)
                        (recyclerViewShopOffer.adapter as ShopOffer).notifyDataSetChanged()
                    }
                    lastClicked = ""

                    textViewShopCoin.text = GameFlow.numberFormatString(moneyReq * -1)

                    animationRefresh = ValueAnimator.ofFloat(originalCoinY, refresh.y).apply {
                        duration = 400
                        addUpdateListener {
                            imageViewShopCoin.y = it.animatedValue as Float
                            textViewShopCoin.y = it.animatedValue as Float - textViewShopCoin.height
                        }
                        addListener(object : Animator.AnimatorListener {
                            override fun onAnimationRepeat(animation: Animator?) {
                            }

                            override fun onAnimationCancel(animation: Animator?) {
                            }

                            override fun onAnimationStart(animation: Animator?) {
                                imageViewShopCoin.visibility = View.VISIBLE
                                textViewShopCoin.visibility = View.VISIBLE
                            }

                            override fun onAnimationEnd(animation: Animator?) {
                                imageViewShopCoin.visibility = View.GONE
                                textViewShopCoin.visibility = View.GONE
                            }
                        })
                        start()
                    }
                }
            }
        }
    }

    private class ShopInventory(
            val parent: Activity_Shop,
            val playerS:Player,
            val textViewInfoItem: CustomTextView,
            val viewInflater:View,
            val listView: RecyclerView
    ) :
            RecyclerView.Adapter<ShopInventory.CategoryViewHolder>() {

        var inflater: View? = null

        class CategoryViewHolder(
                val itemInventory1: ImageView,
                val itemInventory2: ImageView,
                val itemInventory3: ImageView,
                val itemInventory4: ImageView,
                inflater: View,
                val viewGroup: ViewGroup
        ): RecyclerView.ViewHolder(inflater)

        override fun getItemCount() = Data.player.inventorySlots / 4 + 1

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            inflater = LayoutInflater.from(parent.context).inflate(R.layout.row_shop_inventory, parent, false)
            return CategoryViewHolder(
                    inflater!!.imageViewRowShopItem1,
                    inflater!!.imageViewRowShopItem2,
                    inflater!!.imageViewRowShopItem3,
                    inflater!!.imageViewRowShopItem4,
                    inflater ?: LayoutInflater.from(parent.context).inflate(R.layout.row_shop_inventory, parent, false),
                    parent
            )
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(viewHolder: CategoryViewHolder, position: Int) {
            val index:Int = if(position == 0) 0 else{
                position*4
            }

            class Node(
                    val index: Int = 0,
                    val component: ImageView
            ){
                init {
                    if(this.index < playerS.inventory.size ){
                        if(playerS.inventory[this.index] != null){
                            component.setImageBitmap(playerS.inventory[this.index]?.bitmap)
                            component.setBackgroundResource(playerS.inventory[this.index]?.getBackground() ?: 0)
                            component.isClickable = true
                            component.isEnabled = true
                        }else{
                            component.setImageResource(0)
                            component.setBackgroundResource(R.drawable.emptyslot)
                            component.isClickable = false
                        }
                        component.background.clearColorFilter()
                    }else{
                        component.isClickable = false
                        component.isEnabled = false
                        component.setImageResource(0)
                        component.setBackgroundResource(R.drawable.emptyslot_disabled)
                    }

                    component.tag = this.index.toString()
                    component.setOnDragListener(parent.inventoryShopDragListener)

                    component.setOnTouchListener(object : Class_OnSwipeTouchListener(parent, component, true) {
                        override fun onClick(x: Float, y: Float) {
                            super.onClick(x, y)
                            textViewInfoItem.setHTMLText(playerS.inventory[this@Node.index]?.getStatsCompare() ?: "")
                        }

                        override fun onDoubleClick() {
                            super.onDoubleClick()
                            getDoubleClick(this@Node.index, parent, viewInflater, listView, textViewInfoItem, component)
                        }

                        override fun onLongClick() {
                            super.onLongClick()
                            textViewInfoItem.setHTMLText(playerS.inventory[this@Node.index]?.getStatsCompare() ?: "")

                            if(Data.player.shopOffer[this@Node.index] != null){
                                val item = ClipData.Item(this@Node.index.toString())

                                val dragData = ClipData(
                                        "inventory-shop",
                                        arrayOf(this@Node.index.toString()),
                                        item)

                                // Instantiates the drag shadow builder.
                                val myShadow = SystemFlow.ItemDragListener(component)

                                // Starts the drag
                                component.startDrag(
                                        dragData,   // the data to be dragged
                                        myShadow,   // the drag shadow builder
                                        null,       // no need to use local data
                                        0           // flags (not currently used, set to 0)
                                )
                            }
                        }
                    })
                }

            }

            Node(index, viewHolder.itemInventory1)
            Node(index + 1, viewHolder.itemInventory2)
            Node(index + 2, viewHolder.itemInventory3)
            Node(index + 3, viewHolder.itemInventory4)
        }
    }

    private class ShopOffer(
            val textViewInfoItem: CustomTextView,
            private val context: Context
    ) :
            RecyclerView.Adapter<ShopOffer.CategoryViewHolder>() {

        var inflater: View? = null

        class CategoryViewHolder(
                val itemOffer1: ImageView,
                val itemOffer2: ImageView,
                val itemOffer3: ImageView,
                val itemOffer4: ImageView,
                inflater: View,
                val viewGroup: ViewGroup
        ): RecyclerView.ViewHolder(inflater)

        override fun getItemCount() = 2

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            inflater = LayoutInflater.from(parent.context).inflate(R.layout.row_shop_offer, parent, false)
            return CategoryViewHolder(
                    inflater!!.imageViewRowShopOffer1,
                    inflater!!.imageViewRowShopOffer2,
                    inflater!!.imageViewRowShopOffer3,
                    inflater!!.imageViewRowShopOffer4,
                    inflater ?: LayoutInflater.from(parent.context).inflate(R.layout.row_shop_offer, parent, false),
                    parent
            )
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(viewHolder: CategoryViewHolder, position: Int) {
            val index:Int = if(position == 0) 0 else{
                position * 4
            }

            class Node(
                    val index: Int = 0,
                    val component: ImageView
            ){
                init {
                    if(Data.player.shopOffer[this.index] != null){
                        this.component.apply {
                            setImageBitmap(Data.player.shopOffer[this@Node.index]?.bitmap)
                            setBackgroundResource(Data.player.shopOffer[this@Node.index]?.getBackground() ?: 0)
                            isClickable = true
                        }
                    }else this.component.isClickable = false

                    component.tag = this.index.toString()

                    component.setOnTouchListener(object : Class_OnSwipeTouchListener(context, component, true) {
                        override fun onClick(x: Float, y: Float) {
                            super.onClick(x, y)
                            textViewInfoItem.setHTMLText(Data.player.shopOffer[this@Node.index]?.getStatsCompare(true) ?: "")
                            Log.d("onClick_shop", "called")
                        }

                        override fun onLongClick() {
                            super.onLongClick()

                            textViewInfoItem.setHTMLText(Data.player.shopOffer[this@Node.index]?.getStatsCompare(true) ?: "")

                            if(Data.player.shopOffer[this@Node.index] != null){
                                val item = ClipData.Item(this@Node.index.toString())

                                // Create a new ClipData using the tag as a label, the plain text MIME type, and
                                // the already-created item. This will create a new ClipDescription object within the
                                // ClipData, and set its MIME type entry to "text/plain"
                                val dragData = ClipData(
                                        "offer",
                                        arrayOf(this@Node.index.toString()),
                                        item)

                                // Instantiates the drag shadow builder.
                                val myShadow = SystemFlow.ItemDragListener(component)

                                // Starts the drag
                                component.startDrag(
                                        dragData,   // the data to be dragged
                                        myShadow,   // the drag shadow builder
                                        null,       // no need to use local data
                                        0           // flags (not currently used, set to 0)
                                )
                            }
                        }
                    })
                }
            }

            Node(index + 0, viewHolder.itemOffer1)
            Node(index + 1, viewHolder.itemOffer2)
            Node(index + 2, viewHolder.itemOffer3)
            Node(index + 3, viewHolder.itemOffer4)
        }
    }

    companion object {
        private fun getDoubleClick(index:Int, parent: SystemFlow.GameActivity, view:View, listViewInventoryShop: RecyclerView, textViewInfoItem:TextView, button: View){
            val window = PopupWindow(parent)
            window.contentView = view
            view.textViewDialogInfo.setHTMLText("Are you sure you want to sell ${Data.player.inventory[index]?.name} ?")
            window.isOutsideTouchable = false
            window.isFocusable = true
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            view.buttonDialogAccept.setOnClickListener {
                val coords = intArrayOf(0, 0)
                button.getLocationOnScreen(coords)
                val reward = Reward()
                reward.cubeCoins = Data.player.inventory[index]?.priceCubeCoins ?: 0
                reward.experience = 0
                SystemFlow.playComponentSound(parent, R.raw.basic_purchase)
                SystemFlow.visualizeReward(parent, Coordinates(coords[0].toFloat(), coords[1].toFloat()), reward, (parent as? Activity_Shop)?.propertiesBar)

                Data.player.inventory[index] = null
                (listViewInventoryShop.adapter as? ShopInventory)?.notifyDataSetChanged()
                textViewInfoItem.visibility = View.INVISIBLE
                window.dismiss()
            }
            view.imageViewDialogClose.setOnClickListener {
                window.dismiss()
            }
            window.showAtLocation(view, Gravity.CENTER,0,0)
        }

        private fun getDoubleClickOffer(index: Int, viewIndex: Int, textViewInfoItem: TextView, listViewInventoryShop: RecyclerView, propertiesBar: SystemFlow.GamePropertiesBar){
            if(Data.player.cubeCoins >= Data.player.shopOffer[index]?.priceCubeCoins ?: 0 && Data.player.cubix >= Data.player.shopOffer[index]?.priceCubix ?: 0){
                if(Data.player.inventory[viewIndex] == null){
                    SystemFlow.playComponentSound(textViewInfoItem.context, R.raw.basic_purchase)
                    Data.player.cubeCoins -= Data.player.shopOffer[index]?.priceCubeCoins ?: 0
                    Data.player.shopOffer[index]!!.priceCubeCoins /= 2
                    Data.player.inventory[viewIndex] = Data.player.shopOffer[index]
                    Data.player.shopOffer[index] = GameFlow.generateItem(Data.player)
                    textViewInfoItem.visibility = View.INVISIBLE
                    propertiesBar.updateProperties()
                }else{
                    SystemFlow.vibrateAsError(textViewInfoItem.context)
                    listViewInventoryShop.startAnimation(AnimationUtils.loadAnimation(textViewInfoItem.context, R.anim.animation_shaky_short))
                    //Snackbar.make(textViewInfoItem, "Not enough space!", Snackbar.LENGTH_SHORT).show()
                }
            }else{
                SystemFlow.vibrateAsError(textViewInfoItem.context)
                Snackbar.make(textViewInfoItem, "Not enough cube coins!", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    val inventoryShopDragListener = View.OnDragListener { v, event ->               //used in Fragment_Board_Character_Profile
        val itemIndex: Int
        val item: Item?

        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                if (event.clipDescription.label == "offer") {
                    itemIndex = event.clipDescription.getMimeType(0).toInt()
                    item = Data.player.shopOffer[itemIndex]
                    val viewIndex = v?.tag?.toString()?.toIntOrNull()

                    if(item != null && viewIndex != null && Data.player.inventory[viewIndex] == null) {
                        v.background?.setColorFilter(this.resources.getColor(R.color.loginColor_2), PorterDuff.Mode.SRC_ATOP)
                        v.invalidate()

                        true
                    }else Data.player.inventory.contains(null)

                } else {
                    false
                }
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                if (event.clipDescription.label == "offer") {
                    itemIndex = event.clipDescription.getMimeType(0).toInt()
                    item = Data.player.shopOffer[itemIndex]
                    val viewIndex = v?.tag?.toString()?.toIntOrNull()

                    if(item != null && viewIndex != null && Data.player.inventory[viewIndex] == null) {
                        v.background?.setColorFilter(Color.YELLOW, PorterDuff.Mode.SRC_ATOP)
                        v.invalidate()

                        true
                    }else Data.player.inventory.contains(null)

                } else {
                    false
                }
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                if (event.clipDescription.label == "offer") {
                    itemIndex = event.clipDescription.getMimeType(0).toInt()
                    item = Data.player.shopOffer[itemIndex]
                    val viewIndex = v?.tag?.toString()?.toIntOrNull()

                    if(item != null && viewIndex != null && Data.player.inventory[viewIndex] == null) {
                        v.background?.setColorFilter(this.resources.getColor(R.color.loginColor_2), PorterDuff.Mode.SRC_ATOP)
                        v.invalidate()

                        true
                    }else Data.player.inventory.contains(null)

                } else {
                    false
                }
            }

            DragEvent.ACTION_DROP -> {
                if (event.clipDescription.label == "offer") {
                    itemIndex = event.clipDescription.getMimeType(0).toInt()
                    item = Data.player.shopOffer[itemIndex]
                    val viewIndex = v?.tag?.toString()?.toIntOrNull()

                    if(item != null){
                        if(viewIndex != null && Data.player.inventory[viewIndex] == null) {
                            (v as ImageView?)?.background?.clearColorFilter()
                            v.invalidate()

                            getDoubleClickOffer(itemIndex, viewIndex, textViewShopItemInfo, recyclerViewShopInventory, propertiesBar)

                            true
                        }else if(Data.player.inventory.contains(null)){
                            getDoubleClickOffer(itemIndex, Data.player.inventory.indexOf(null), textViewShopItemInfo, recyclerViewShopInventory, propertiesBar)

                            true
                        }else false
                    } else false

                } else {
                    false
                }
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                v.post {
                    this.refreshListViews()
                }

                true
            }
            else -> {
                false
            }
        }
    }

    val shopOfferDragListener = View.OnDragListener { v, event ->               //used in Fragment_Board_Character_Profile
        val itemIndex: Int
        val item: Item?

        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                if (event.clipDescription.label == "inventory-shop") {
                    itemIndex = event.clipDescription.getMimeType(0).toInt()
                    item = Data.player.inventory[itemIndex]
                    item != null

                } else {
                    false
                }
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                if (event.clipDescription.label == "inventory-shop") {
                    itemIndex = event.clipDescription.getMimeType(0).toInt()
                    item = Data.player.inventory[itemIndex]
                    item != null

                } else {
                    false
                }
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                if (event.clipDescription.label == "inventory-shop") {
                    itemIndex = event.clipDescription.getMimeType(0).toInt()
                    item = Data.player.inventory[itemIndex]
                    item != null

                } else {
                    false
                }
            }

            DragEvent.ACTION_DROP -> {
                if (event.clipDescription.label == "inventory-shop") {
                    itemIndex = event.clipDescription.getMimeType(0).toInt()
                    item = Data.player.inventory[itemIndex]

                    if(item != null) {

                        getDoubleClick(itemIndex, this, layoutInflater.inflate(R.layout.popup_dialog,null), recyclerViewShopInventory, textViewShopItemInfo, v)

                        true
                    }else false

                } else {
                    false
                }
            }

            DragEvent.ACTION_DRAG_ENDED -> {
                this.refreshListViews()

                true
            }
            else -> {
                false
            }
        }
    }
}