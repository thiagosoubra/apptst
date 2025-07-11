package com.agospace.bokob

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpacingItemDecoration(
    private val spanCount: Int,
    private val spacing: Int,
    private val edgePadding: Int
) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State
    ) {
        val position = parent.getChildAdapterPosition(view)
        val adapter = parent.adapter ?: return

        if (position == RecyclerView.NO_POSITION) return

        val viewType = adapter.getItemViewType(position)

        // HEADER não recebe padding/margem
        if (viewType == 0 /* VIEW_TYPE_HEADER */) {
            outRect.set(0, 0, 0, 0)
            return
        }

        // Laterais sempre têm edgePadding, topo e baixo têm spacing
        outRect.left = edgePadding
        outRect.right = edgePadding
        outRect.top = spacing
        outRect.bottom = spacing
    }

}
