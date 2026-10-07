package com.wazuhonde.asfield.ui

import android.annotation.SuppressLint
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.wazuhonde.asfield.databinding.ItemActivityEventBinding
import com.wazuhonde.asfield.model.ActivityEvent

class ActivityEventAdapter : RecyclerView.Adapter<ActivityEventAdapter.EventViewHolder>() {
    private val events = mutableListOf<ActivityEvent>()

    @SuppressLint("NotifyDataSetChanged")
    fun submitList(newList: List<ActivityEvent>) {
        events.clear()
        events.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        return EventViewHolder(
            ItemActivityEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(events[position])
    }

    override fun getItemCount(): Int = events.size

    class EventViewHolder(private val binding: ItemActivityEventBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(event: ActivityEvent) {
            binding.tvActivityMessage.text = event.message
            binding.tvActivityTime.text = DateUtils.getRelativeTimeSpanString(
                event.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )
        }
    }
}
