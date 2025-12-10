package com.example.saktahahathonv1.family

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.saktahahathonv1.R

class ChildrenAdapter(
    private var children: List<Child>,
    private val onShowOnMap: (Child) -> Unit,
    private val onNotify: (Child) -> Unit
) : RecyclerView.Adapter<ChildrenAdapter.ChildViewHolder>() {

    class ChildViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtAvatar: TextView = view.findViewById(R.id.txtAvatar)
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtStatus: TextView = view.findViewById(R.id.txtStatus)
        val imgStatus: ImageView = view.findViewById(R.id.imgStatus)
        val btnShowOnMap: ImageButton = view.findViewById(R.id.btnShowOnMap)
        val btnNotify: ImageButton = view.findViewById(R.id.btnNotify)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChildViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_child, parent, false)
        return ChildViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChildViewHolder, position: Int) {
        val child = children[position]

        holder.txtAvatar.text = child.getAvatar()
        holder.txtName.text = child.name
        holder.txtStatus.text = child.getStatusText()

        // Установка цвета статуса
        val statusColor = if (child.isInSafeZone) {
            ContextCompat.getColor(holder.itemView.context, R.color.success)
        } else {
            ContextCompat.getColor(holder.itemView.context, R.color.secondary)
        }
        holder.imgStatus.setColorFilter(statusColor)

        holder.btnShowOnMap.setOnClickListener {
            onShowOnMap(child)
        }

        holder.btnNotify.setOnClickListener {
            onNotify(child)
        }
    }

    override fun getItemCount() = children.size

    fun updateChildren(newChildren: List<Child>) {
        children = newChildren
        notifyDataSetChanged()
    }
}
