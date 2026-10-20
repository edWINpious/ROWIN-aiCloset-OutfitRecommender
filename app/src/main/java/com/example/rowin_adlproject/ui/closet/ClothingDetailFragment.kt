package com.example.rowin_adlproject.ui.closet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.rowin_adlproject.MainActivity
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.databinding.ScreenClothingDetailBinding
import com.example.rowin_adlproject.databinding.SheetDeleteItemBinding
import com.example.rowin_adlproject.ui.components.displayName
import androidx.lifecycle.lifecycleScope
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.Loaders
import com.example.rowin_adlproject.ui.view.Screen
import com.example.rowin_adlproject.ui.view.ScreenHost
import com.example.rowin_adlproject.ui.view.SheetDialogFragment
import com.example.rowin_adlproject.ui.view.Views
import kotlinx.coroutines.launch

/**
 * Feature #7 - Clothing Details: full info, AI attributes, Edit and Delete
 * (with confirmation bottom sheet per feature #18).
 */
class ClothingDetailFragment : HostedFragment(R.layout.screen_clothing_detail) {

    private var _b: ScreenClothingDetailBinding? = null
    private val b get() = _b!!

    private val itemId: Long
        get() = arguments?.getLong(MainActivity.ARG_ITEM_ID) ?: 0L

    private lateinit var vm: ClosetViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenClothingDetailBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        vm = androidx.lifecycle.ViewModelProvider(
            this, Loaders.closet(requireContext(), host.userId),
        )[ClosetViewModel::class.java]

        b.detailBack.setOnClickListener { host.back() }
        b.detailDelete.setOnClickListener {
            val sheet = DeleteItemSheet().apply {
                arguments = Bundle().apply { putLong(MainActivity.ARG_ITEM_ID, itemId) }
            }
            sheet.showOnce(childFragmentManager, "delete_item_sheet")
        }

        vm.repository.observeClothingById(itemId)
            .collectFlowWithViewLifecycle { item -> bind(item) }
    }

    private fun bind(item: ClothingItemEntity?) {
        if (item == null) return
        b.detailEdit.setOnClickListener { host.open(Screen.EDIT_CLOTHING, item.id) }
        val bitmap = Views.decodeSampled(
            item.imageFileName?.let { vm.imageStore.fileFor(it) }, 900,
        )
        if (bitmap != null) {
            b.detailImage.setImageBitmap(bitmap)
            Views.hide(b.detailImagePlaceholder)
        } else {
            b.detailImage.setImageDrawable(null)
            Views.show(b.detailImagePlaceholder)
        }
        b.detailName.text = item.name
        b.detailCategory.text = item.category.displayName()
        b.detailColor.text = Views.detailValue(item.color)
        b.detailSeason.text = item.season.displayName()
        b.detailOccasion.text = item.occasion.displayName()

        val aiValue = Views.fold(item.pattern, item.style, item.material)
        if (aiValue.isNotEmpty()) {
            b.detailAiValue.text = aiValue
            Views.show(b.detailAiCard)
        } else {
            Views.hide(b.detailAiCard)
        }

        if (!item.aiDescription.isNullOrBlank()) {
            b.detailDesc.text = item.aiDescription
            Views.show(b.detailDescCard)
        } else {
            Views.hide(b.detailDescCard)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    /** Delete confirmation (feature #18, per the Stitch design). */
    class DeleteItemSheet : SheetDialogFragment(R.layout.sheet_delete_item) {

        override fun onSheetReady(view: View) {
            val binding = SheetDeleteItemBinding.bind(view)
            val host = requireActivity() as ScreenHost
            val repository = host.rowin.repository
            val itemId = arguments?.getLong(MainActivity.ARG_ITEM_ID) ?: -1L

            lifecycleScope.launch {
                val item = repository.getClothingById(itemId)
                binding.sheetDeleteMessage.text =
                    getString(R.string.delete_item_message, item?.name ?: "")
            }

            binding.sheetDeleteConfirm.setOnClickListener {
                lifecycleScope.launch {
                    val item = repository.getClothingById(itemId)
                    if (item != null) repository.deleteClothing(item)
                    dismiss()
                    host.back()
                }
            }
            binding.sheetDeleteCancel.setOnClickListener { dismiss() }
        }
    }
}
