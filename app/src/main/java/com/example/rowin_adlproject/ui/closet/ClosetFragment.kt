package com.example.rowin_adlproject.ui.closet

import android.graphics.Rect
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.Category
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.databinding.ItemChipBinding
import com.example.rowin_adlproject.databinding.ItemClothingCardBinding
import com.example.rowin_adlproject.databinding.ScreenMyClosetBinding
import com.example.rowin_adlproject.ui.components.displayName
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.Loaders
import com.example.rowin_adlproject.ui.view.Screen
import com.example.rowin_adlproject.ui.view.Views
import com.example.rowin_adlproject.ui.view.onTextChanged

/**
 * Features #3-#5: My Closet - all items as cards, search, category filtering,
 * FAB to Add Clothing, and the informative empty state (#19).
 */
class ClosetFragment : HostedFragment(R.layout.screen_my_closet) {

    private var _b: ScreenMyClosetBinding? = null
    private val b get() = _b!!

    private lateinit var vm: ClosetViewModel
    private lateinit var chipsAdapter: ChipsAdapter
    private lateinit var gridAdapter: ClothingGridAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenMyClosetBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        vm = androidx.lifecycle.ViewModelProvider(
            this, Loaders.closet(requireContext(), host.userId),
        )[ClosetViewModel::class.java]

        chipsAdapter = ChipsAdapter { category ->
            val next = if (vm.selectedCategory.value == category) null else category
            vm.selectedCategory.value = next
            chipsAdapter.selectedCategory = next
            chipsAdapter.notifyDataSetChanged()
        }
        b.closetChips.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.closetChips.adapter = chipsAdapter

        gridAdapter = ClothingGridAdapter(vm::imageFileFor) { item ->
            host.open(Screen.CLOTHING_DETAIL, item.id)
        }
        b.closetGrid.layoutManager = GridLayoutManager(requireContext(), 2)
        b.closetGrid.addItemDecoration(GridSpacing())
        b.closetGrid.adapter = gridAdapter

        b.closetSearch.onTextChanged { query ->
            vm.searchQuery.value = query
        }
        b.closetFab.setOnClickListener { host.open(Screen.ADD_CLOTHING) }
        b.closetEmptyAction.setOnClickListener { host.open(Screen.ADD_CLOTHING) }

        vm.searchQuery.collectWithViewLifecycle { query ->
            if (b.closetSearch.text?.toString() != query) b.closetSearch.setText(query)
        }
        vm.items.collectWithViewLifecycle { items ->
            gridAdapter.submit(items)
            val pristine = vm.searchQuery.value.isBlank() && vm.selectedCategory.value == null
            b.closetEmptyTitle.text =
                if (pristine) getString(R.string.empty_closet_title) else getString(R.string.empty_results_title)
            b.closetEmptyDesc.text =
                if (pristine) getString(R.string.empty_closet_desc) else getString(R.string.empty_results_desc)
            b.closetEmptyAction.visibility =
                if (pristine) View.VISIBLE else View.GONE
            Views.toggle(
                showViews = if (items.isEmpty()) listOf(b.closetEmpty) else listOf(b.closetGrid),
                hideViews = if (items.isEmpty()) listOf(b.closetGrid) else listOf(b.closetEmpty),
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    // ------------------------------------------------------------- chips

    private class ChipsAdapter(
        private val onSelect: (Category?) -> Unit,
    ) : RecyclerView.Adapter<ChipsAdapter.Holder>() {

        var selectedCategory: Category? = null

        /** "All" + every category. */
        val entries: List<Category?> = listOf(null) + Category.entries.toList()

        class Holder(val binding: ItemChipBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemChipBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = entries.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val category = entries[position]
            val selected = category == selectedCategory
            val context = holder.binding.root.context
            holder.binding.chipLabel.text = category?.displayName() ?: context.getString(R.string.chip_all)
            holder.binding.chipLabel.setBackgroundResource(
                if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip,
            )
            holder.binding.chipLabel.setTextColor(
                context.getColor(if (selected) R.color.rowin_on_primary else R.color.rowin_on_surface_variant),
            )
            holder.binding.chipLabel.setOnClickListener { onSelect(category) }
        }
    }

    // -------------------------------------------------------------- grid

    private class ClothingGridAdapter(
        private val imageFileFor: (ClothingItemEntity) -> java.io.File?,
        private val onClick: (ClothingItemEntity) -> Unit,
    ) : RecyclerView.Adapter<ClothingGridAdapter.Holder>() {

        private val items = mutableListOf<ClothingItemEntity>()

        fun submit(newItems: List<ClothingItemEntity>) {
            items.clear()
            items += newItems
            notifyDataSetChanged()
        }

        class Holder(val binding: ItemClothingCardBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemClothingCardBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val binding = holder.binding
            val bitmap = Views.decodeSampled(imageFileFor(item), 480)
            if (bitmap != null) {
                binding.clothingImage.setImageBitmap(bitmap)
                Views.hide(binding.clothingImagePlaceholder)
            } else {
                binding.clothingImage.setImageDrawable(null)
                Views.show(binding.clothingImagePlaceholder)
            }
            binding.clothingName.text = item.name
            binding.clothingCaption.text = "${item.category.displayName()} - ${item.occasion.displayName()}"
            binding.clothingCard.setOnClickListener { onClick(item) }
        }
    }

    /** 12dp column gap, 16dp row gap (LazyVerticalGrid spacing equivalent). */
    private class GridSpacing : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State,
        ) {
            val span = (view.layoutParams as? GridLayoutManager.LayoutParams)?.spanIndex ?: 0
            outRect.left = if (span == 1) dp(view, 6) else 0
            outRect.right = if (span == 0) dp(view, 6) else 0
            outRect.bottom = dp(view, 16)
        }

        private fun dp(view: View, value: Int): Int =
            (value * view.resources.displayMetrics.density).toInt()
    }
}
