package org.viptv.app

import java.util.concurrent.CopyOnWriteArrayList

/**
 * What a MockEngine handler saw, recorded on the engine's dispatcher while the test thread reads it.
 *
 * Copy-on-write so a handler appending during a read can never throw ConcurrentModificationException, and every
 * iteration sees the snapshot it started with. Tests read it as a plain List; there is no live view to opt out of.
 */
internal class EngineRequests<T> : AbstractMutableList<T>() {
    private val recorded = CopyOnWriteArrayList<T>()

    override val size: Int get() = recorded.size

    override fun get(index: Int): T = recorded[index]

    override fun add(index: Int, element: T) = recorded.add(index, element)

    override fun add(element: T): Boolean = recorded.add(element)

    override fun set(index: Int, element: T): T = recorded.set(index, element)

    override fun removeAt(index: Int): T = recorded.removeAt(index)

    override fun iterator(): MutableIterator<T> = recorded.iterator()

    override fun listIterator(): MutableListIterator<T> = recorded.listIterator()

    override fun listIterator(index: Int): MutableListIterator<T> = recorded.listIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> = recorded.subList(fromIndex, toIndex).toMutableList()

    override fun clear() = recorded.clear()

    override fun removeAll(elements: Collection<T>): Boolean = recorded.removeAll(elements.toSet())

    override fun retainAll(elements: Collection<T>): Boolean = recorded.retainAll(elements.toSet())

    fun snapshot(): List<T> = recorded.toList()
}
