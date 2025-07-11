package com.agospace.bokob

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MixedAdapter(
    private val items: List<OibleListItem>,
    private val onBookClick: (OibleBook) -> Unit // Changed to receive OibleBook directly
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_SINGLE = 1
        private const val VIEW_TYPE_GROUP = 2
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_PAIR = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is OibleListItem.OibleHeader -> VIEW_TYPE_HEADER
            is OibleListItem.SingleBook -> VIEW_TYPE_SINGLE
            is OibleListItem.SingleBookPair -> VIEW_TYPE_PAIR
            is OibleListItem.BookGroup -> VIEW_TYPE_GROUP
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.rv_header, parent, false)
                HeaderViewHolder(view)
            }
            VIEW_TYPE_SINGLE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.card_item, parent, false)
                SingleBookViewHolder(view, onBookClick)
            }
            VIEW_TYPE_GROUP -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.group_layout, parent, false)
                GroupViewHolder(view, onBookClick)
            }
            VIEW_TYPE_PAIR -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_pair_layout, parent, false)
                PairViewHolder(view, onBookClick)
            }
            else -> throw IllegalArgumentException("Unknown view type")
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is OibleListItem.OibleHeader -> { /* nothing to do */ }
            is OibleListItem.SingleBook -> { /* not used anymore */ }
            is OibleListItem.SingleBookPair -> (holder as PairViewHolder).bind(item)
            is OibleListItem.BookGroup -> (holder as GroupViewHolder).bind(item)
        }
    }

    class SingleBookViewHolder(
        itemView: View,
        private val onBookClick: (OibleBook) -> Unit // Changed to receive OibleBook
    ) : RecyclerView.ViewHolder(itemView) {
        private val coverImage: ImageView = itemView.findViewById(R.id.coverImage)
        private val titleImage: ImageView = itemView.findViewById(R.id.ImageTitle)
        private val titleText: TextView = itemView.findViewById(R.id.titleText)

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

    class GroupViewHolder(
        itemView: View,
        private val onBookClick: (OibleBook) -> Unit // Changed to receive OibleBook
    ) : RecyclerView.ViewHolder(itemView) {
        private val groupTitle: TextView = itemView.findViewById(R.id.groupTitle)
        private val horizontalRecyclerView: RecyclerView = itemView.findViewById(R.id.horizontalRecyclerView)

        fun bind(groupItem: OibleListItem.BookGroup) {
            groupTitle.text = "${groupItem.seriesTitle}"
            horizontalRecyclerView.layoutManager = LinearLayoutManager(itemView.context, LinearLayoutManager.HORIZONTAL, false)
            val orderedBooks = groupItem.books.sortedBy { book ->
                book.series?.index?.substringBefore("/")?.toIntOrNull() ?: 0
            }
            horizontalRecyclerView.adapter = HorizontalAdapter(orderedBooks, onBookClick) // Pass the lambda
        }
    }
    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view)

    class PairViewHolder(
        view: View,
        private val onBookClick: (OibleBook) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val firstCard: ViewGroup = view.findViewById(R.id.card1)
        private val secondCard: ViewGroup = view.findViewById(R.id.card2)

        fun bind(pair: OibleListItem.SingleBookPair) {
            val (book1, book2) = pair

            val title1 = firstCard.findViewById<TextView>(R.id.titleText)
            val cover1 = firstCard.findViewById<ImageView>(R.id.coverImage)
            val titleImg1 = firstCard.findViewById<ImageView>(R.id.ImageTitle)
            title1.text = book1.title

            cover1.setImageURI(Uri.parse(book1.coverImagePath))

            if (book1.titleImagePath != null) {
                titleImg1.setImageURI(Uri.parse(book1.titleImagePath))
                titleImg1.visibility = View.VISIBLE
            } else {
                titleImg1.visibility = View.GONE
            }

            firstCard.setOnClickListener { onBookClick(book1) }

            if (book2 != null) {
                secondCard.visibility = View.VISIBLE

                val title2 = secondCard.findViewById<TextView>(R.id.titleText)
                val cover2 = secondCard.findViewById<ImageView>(R.id.coverImage)
                val titleImg2 = secondCard.findViewById<ImageView>(R.id.ImageTitle)

                title2.text = book2.title
                cover2.setImageURI(Uri.parse(book2.coverImagePath))

                if (book2.titleImagePath != null) {
                    titleImg2.setImageURI(Uri.parse(book2.titleImagePath))
                    titleImg2.visibility = View.VISIBLE
                } else {
                    titleImg2.visibility = View.GONE
                }

                secondCard.setOnClickListener { onBookClick(book2) }
            } else {
                secondCard.visibility = View.INVISIBLE
            }
        }
    }
}