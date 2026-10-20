package com.example.rowin_adlproject.ui.outfits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rowin_adlproject.MainActivity
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.data.OutfitWithItems
import com.example.rowin_adlproject.databinding.ItemBreakdownBinding
import com.example.rowin_adlproject.databinding.ScreenOutfitDetailBinding
import com.example.rowin_adlproject.databinding.SheetRemoveOutfitBinding
import com.example.rowin_adlproject.ui.components.displayName
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.collectOnStarted
import com.example.rowin_adlproject.ui.view.Loaders
import com.example.rowin_adlproject.ui.view.Screen
import com.example.rowin_adlproject.ui.view.SheetDialogFragment
import com.example.rowin_adlproject.ui.view.Views
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Feature #10 - Outfit Details: name, occasion, season, created date, the
 * item breakdown rail, plus Edit Outfit (#12) and Remove Outfit (#13, with
 * the confirmation bottom sheet from #18).
 */
class OutfitDetailFragment : HostedFragment(R.layout.screen_outfit_detail) {

    private var _b: ScreenOutfitDetailBinding? = null
    private val b get() = _b!!

    private val outfitId: Long
        get() = arguments?.getLong(MainActivity.ARG_ITEM_ID) ?: 0L

    private lateinit var vm: OutfitsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenOutfitDetailBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        vm = androidx.lifecycle.ViewModelProvider(
            this, Loaders.outfits(requireContext(), host.userId),
        )[OutfitsViewModel::class.java]

        b.outfitDetailBack.setOnClickListener { host.back() }
        b.outfitDetailRemove.setOnClickListener {
            RemoveOutfitSheet.forOutfit(outfitId).showOnce(childFragmentManager, "remove_outfit_sheet")
        }
        b.outfitDetailRemoveButton.setOnClickListener {
            RemoveOutfitSheet.forOutfit(outfitId).showOnce(childFragmentManager, "remove_outfit_sheet")
        }
        b.outfitDetailEdit.setOnClickListener { host.open(Screen.EDIT_OUTFIT, outfitId) }

        b.outfitBreakdownRail.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        vm.outfit(outfitId).collectWithViewLifecycle { outfit -> bind(outfit) }
    }

    private fun bind(outfit: OutfitWithItems?) {
        if (outfit == null) {
            Views.show(b.outfitDetailLoading)
            return
        }
        Views.hide(b.outfitDetailLoading)
        b.outfitHeroName.text = outfit.outfit.name

        val heroItem = outfit.items.firstOrNull { it.imageFileName != null }
        val bitmap = Views.decodeSampled(heroItem?.let { vm.imageFileFor(it) }, 900)
        if (bitmap != null) {
            b.outfitHeroImage.setImageBitmap(bitmap)
            Views.hide(b.outfitHeroPlaceholder)
        } else {
            b.outfitHeroImage.setImageDrawable(null)
            Views.show(b.outfitHeroPlaceholder)
        }

        b.outfitDetailSeason.text = outfit.outfit.season.displayName()
        b.outfitDetailOccasion.text = outfit.outfit.occasion.displayName()
        b.outfitDetailCreated.text = SimpleDateFormat("EEEE, MMMM d'th' yyyy", Locale.getDefault())
            .format(Date(outfit.outfit.createdAt))

        val note = outfit.outfit.aiNote
        if (!note.isNullOrBlank()) {
            b.outfitDetailAiNote.text = "  $note"
            Views.show(b.outfitDetailAiNoteCard)
        } else {
            Views.hide(b.outfitDetailAiNoteCard)
        }

        b.outfitBreakdownRail.adapter = BreakdownAdapter(outfit.items, vm::imageFileFor)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    // --------------------------------------------------------- breakdown rail

    private class BreakdownAdapter(
        private val items: List<ClothingItemEntity>,
        private val imageFileFor: (ClothingItemEntity) -> java.io.File?,
    ) : RecyclerView.Adapter<BreakdownAdapter.Holder>() {

        class Holder(val binding: ItemBreakdownBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemBreakdownBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val binding = holder.binding
            val bitmap = Views.decodeSampled(imageFileFor(item), 240)
            if (bitmap != null) {
                binding.breakdownImage.setImageBitmap(bitmap)
                Views.hide(binding.breakdownImagePlaceholder)
            } else {
                binding.breakdownImage.setImageDrawable(null)
                Views.show(binding.breakdownImagePlaceholder)
            }
            binding.breakdownName.text = item.name
            binding.breakdownCategory.text = item.category.displayName()
        }
    }

    /** Remove-outfit confirmation (feature #13 + #18). */
    class RemoveOutfitSheet : SheetDialogFragment(R.layout.sheet_remove_outfit) {

        companion object {
            private const val ARG_OUTFIT = "arg_outfit"

            fun forOutfit(outfitId: Long): RemoveOutfitSheet = RemoveOutfitSheet().apply {
                arguments = Bundle().apply { putLong(ARG_OUTFIT, outfitId) }
            }
        }

        override fun onSheetReady(view: View) {
            val binding = SheetRemoveOutfitBinding.bind(view)
            val host = requireActivity() as com.example.rowin_adlproject.ui.view.ScreenHost
            val vm = androidx.lifecycle.ViewModelProvider(requireParentFragment())[
                OutfitsViewModel::class.java]
            val outfitId = requireArguments().getLong(ARG_OUTFIT)

            val outfitFlow = vm.outfit(outfitId)
            outfitFlow.collectOnStarted(this) { outfit ->
                binding.sheetRemoveMessage.text = getString(
                    R.string.remove_outfit_message, outfit?.outfit?.name ?: "",
                )
            }

            binding.sheetRemoveConfirm.setOnClickListener {
                outfitFlow.value?.let { current -> vm.deleteOutfit(current) }
                dismiss()
                host.back()
            }
            binding.sheetRemoveCancel.setOnClickListener { dismiss() }
        }
    }
}
