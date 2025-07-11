package com.agospace.bokob

sealed class OibleListItem {
    object OibleHeader : OibleListItem()
    data class SingleBook(val book: OibleBook) : OibleListItem() // ainda usada internamente
    data class SingleBookPair(val first: OibleBook, val second: OibleBook?) : OibleListItem()
    data class BookGroup(val seriesTitle: String, val books: List<OibleBook>) : OibleListItem()
}