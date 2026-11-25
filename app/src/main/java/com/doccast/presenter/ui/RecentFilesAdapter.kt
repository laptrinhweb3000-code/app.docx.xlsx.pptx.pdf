package com.doccast.presenter.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.doccast.presenter.databinding.ItemRecentFileBinding
import com.doccast.presenter.model.RecentFileItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecentFilesAdapter(
    private val onItemClick: (RecentFileItem) -> Unit
) : RecyclerView.Adapter<RecentFilesAdapter.ViewHolder>() {

    private val items = mutableListOf<RecentFileItem>()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    fun submitList(newItems: List<RecentFileItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRecentFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.binding.tvFormatBadge.text = item.format.label
        holder.binding.tvFormatBadge.setBackgroundColor(Color.parseColor(item.format.badgeHex))
        holder.binding.tvFileName.text = item.displayName
        holder.binding.tvFileMeta.text = "Mở lúc: ${dateFormat.format(Date(item.lastOpenedAt))}"
        holder.binding.root.setOnClickListener { onItemClick(item) }
    }

    override fun getItemCount(): Int = items.size

    class ViewHolder(val binding: ItemRecentFileBinding) : RecyclerView.ViewHolder(binding.root)
}
