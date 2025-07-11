package com.agospace.bokob

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class HorizontalAdapter(
    private val books: List<OibleBook>,
    private val onBookClick: (OibleBook) -> Unit // Changed to receive OibleBook directly
) : RecyclerView.Adapter<HorizontalAdapter.Holder>() {

    class Holder(
        view: View,
        private val onBookClick: (OibleBook) -> Unit // Changed to receive OibleBook
    ) : RecyclerView.ViewHolder(view) {
        private val coverImage: ImageView = view.findViewById(R.id.coverImage)
        private val titleText: TextView = view.findViewById(R.id.titleText)
        private val titleImage: ImageView = view.findViewById(R.id.ImageTitle)

        fun bind(book: OibleBook) {
            titleText.text = book.title
            coverImage.setImageURI(Uri.parse(book.coverImagePath))

            if (book.titleImagePath != null) {
                titleImage.setImageURI(Uri.parse(book.titleImagePath))
                titleImage.visibility = View.VISIBLE
            } else {
                titleImage.visibility = View.GONE
            }

            itemView.setOnClickListener {
                onBookClick(book) // Pass the entire OibleBook
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.card_item, parent, false)

        val params = RecyclerView.LayoutParams(
            parent.context.resources.getDimensionPixelSize(R.dimen.card_min_size),
            RecyclerView.LayoutParams.WRAP_CONTENT
        )
        view.layoutParams = params

        return Holder(view, onBookClick)
    }

    override fun getItemCount(): Int = books.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val isLast = position == itemCount - 1
        val spacing = holder.itemView.context.resources.getDimensionPixelSize(R.dimen.spacing_between_items)

        val layoutParams = holder.itemView.layoutParams as RecyclerView.LayoutParams
        layoutParams.marginEnd = if (isLast) 0 else spacing
        holder.itemView.layoutParams = layoutParams

        holder.bind(books[position])
    }
}