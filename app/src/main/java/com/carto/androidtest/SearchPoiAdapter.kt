package com.carto.androidtest

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.carto.androidtest.databinding.ItemSearchPoiBinding
import java.util.*

class SearchPoiAdapter(
    private val poiClickedListener: (Poi) -> Unit
) : ListAdapter<Poi, SearchPoiAdapter.PoiViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        PoiViewHolder(
            ItemSearchPoiBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )

    override fun onBindViewHolder(holder: PoiViewHolder, position: Int) =
        holder.bind(getItem(position), poiClickedListener)

    class PoiViewHolder(
        private val binding: ItemSearchPoiBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(poi: Poi, poiClickedListener: (Poi) -> Unit) {
            binding.poiTitle.text = poi.title
            binding.poiDescription.text = poi.description
            binding.direction.setImageResource(getDirectionImage(poi.direction))
            binding.layout.setOnClickListener {
                poiClickedListener(poi)
            }
        }

        private fun getDirectionImage(direction: String): Int =
            when (direction.toUpperCase(Locale.ROOT)) {
                "N" -> R.drawable.direction_n
                "N-E" -> R.drawable.direction_ne
                "E" -> R.drawable.direction_e
                "S-E" -> R.drawable.direction_se
                "S" -> R.drawable.direction_s
                "S-W" -> R.drawable.direction_sw
                "W" -> R.drawable.direction_w
                "N-W" -> R.drawable.direction_nw
                else -> R.drawable.direction_unknown
            }
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Poi>() {
            override fun areItemsTheSame(oldItem: Poi, newItem: Poi): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Poi, newItem: Poi): Boolean =
                oldItem == newItem
        }
    }
}