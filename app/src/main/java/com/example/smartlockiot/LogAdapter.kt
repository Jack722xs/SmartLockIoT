package com.example.smartlockiot

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartlockiot.databinding.ItemLogBinding
import com.example.smartlockiot.model.AccessLog
import java.text.SimpleDateFormat
import java.util.Locale

class LogAdapter : ListAdapter<AccessLog, LogAdapter.VH>(Diff) {

    class VH(val b: ItemLogBinding) : RecyclerView.ViewHolder(b.root)

    object Diff : DiffUtil.ItemCallback<AccessLog>() {
        override fun areItemsTheSame(o: AccessLog, n: AccessLog) =
            o.timestamp == n.timestamp && o.userId == n.userId
        override fun areContentsTheSame(o: AccessLog, n: AccessLog) = o == n
    }

    private val df = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(ItemLogBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = getItem(position)
        val ok = item.status.equals(Constants.STATUS_SUCCESS, ignoreCase = true)
        val color = try {
            Color.parseColor(item.statusColor)
        } catch (e: Exception) {
            if (ok) Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
        }
        h.b.viewDot.backgroundTintList = ColorStateList.valueOf(color)
        h.b.tvLogUser.text = "UID: ${item.userId}"
        h.b.tvLogStatus.text = if (ok) "Apertura exitosa" else "Apertura no exitosa (acceso denegado)"
        h.b.tvLogStatus.setTextColor(color)
        h.b.tvLogTime.text = item.timestamp?.toDate()?.let { df.format(it) } ?: "Sincronizando…"
    }
}
