package cz.cubeit.cubeit

import android.animation.ObjectAnimator
import android.content.Context
import android.net.ConnectivityManager
import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.activity_offline_mg.*
import kotlinx.android.synthetic.main.row_offline_mg_category.view.*

class ActivityOfflineMG: SystemFlow.GameActivity(R.layout.activity_offline_mg, ActivityType.OfflineMG, false){

    fun isConnected(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        textViewOfflineMGTitle.fontSizeType = CustomTextView.SizeType.title
        imageViewOfflineMGBack.setOnClickListener {
            this.finish()
        }

        recyclerViewOfflineMG.apply {
            layoutManager = LinearLayoutManager(this@ActivityOfflineMG)
            adapter = OfflineMgCategories(
                    Data.miniGames,
                    frameLayoutOfflineMG,
                    this@ActivityOfflineMG
            )
        }
    }

    private class OfflineMgCategories(private val miniGames: List<Minigame>, private val infoFrameLayout: FrameLayout, private val parent: ActivityOfflineMG) :
            RecyclerView.Adapter<OfflineMgCategories.CategoryViewHolder>() {

        var inflater: View? = null
        var pinned: Boolean = false

        class CategoryViewHolder(val textViewName: CustomTextView, val textViewNew: TextView, val imageViewBg: ImageView, inflater: View, val viewGroup: ViewGroup): RecyclerView.ViewHolder(inflater)

        override fun getItemCount() = miniGames.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
            inflater = LayoutInflater.from(parent.context).inflate(R.layout.row_offline_mg_category, parent, false)
            return CategoryViewHolder(
                    inflater!!.textViewRowOfflineMgName,
                    inflater!!.textViewRowOfflineMgNew,
                    inflater!!.imageViewRowOfflineMgBg,
                    inflater ?: LayoutInflater.from(parent.context).inflate(R.layout.row_offline_mg_category, parent, false),
                    parent
            )
        }

        override fun onBindViewHolder(viewHolder: CategoryViewHolder, position: Int) {
            viewHolder.textViewName.text = miniGames[position].title
            viewHolder.textViewNew.visibility = if(miniGames[position].isNew){
                View.VISIBLE
            }else View.GONE

            ObjectAnimator.ofInt(if(pinned) 42 else 0, if(pinned) 0 else 42).apply{
                duration = 450
                addUpdateListener {
                    viewHolder.viewGroup.setPadding(3, 6 ,it.animatedValue as Int, 6)
                }
                start()
            }
            if(!pinned){

            }else {
                viewHolder.viewGroup.setPadding(3, 6, 42, 6)
            }

            viewHolder.imageViewBg.setOnClickListener {
                if(viewHolder.textViewNew.visibility != View.GONE){
                    viewHolder.textViewNew.visibility = View.GONE
                }
                pinned = true
                this.notifyDataSetChanged()
                viewHolder.viewGroup.setPadding(6, 6 ,0, 6)
                parent.supportFragmentManager.beginTransaction().replace(infoFrameLayout.id, miniGames[position].getFragmentInstance()).commitAllowingStateLoss()
            }
        }
    }
}