package com.example.rowin_adlproject.ui.outfits

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.rowin_adlproject.MainActivity
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.databinding.ScreenOutfitPlannerBinding
import com.example.rowin_adlproject.databinding.SheetSlotPickerBinding
import com.example.rowin_adlproject.ui.components.displayName
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.RowinDropdowns
import com.example.rowin_adlproject.ui.view.Loaders
import com.example.rowin_adlproject.ui.view.Screen
import com.example.rowin_adlproject.ui.view.SheetDialogFragment
import com.example.rowin_adlproject.ui.view.Views
import com.example.rowin_adlproject.ui.view.onTextChanged

/**
 * Features #8-#9, #12, #21: Outfit Planner. Slot canvas, tap-to-pick from
 * the wardrobe, "Generate Outfit" (Groq AI), name + season + occasion,
 * Save/Update. Also serves Edit Outfit with a back header.
 */
class OutfitPlannerFragment : HostedFragment(R.layout.screen_outfit_planner) {

    private var _b: ScreenOutfitPlannerBinding? = null
    private val b get() = _b!!

    private val editingOutfitId: Long?
        get() = arguments?.getLong(MainActivity.ARG_ITEM_ID, -1L)?.takeIf { it > 0 }

    private lateinit var vm: PlannerViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenOutfitPlannerBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        vm = androidx.lifecycle.ViewModelProvider(
            this, Loaders.planner(requireContext(), host.userId, editingOutfitId),
        )[PlannerViewModel::class.java]

        val editMode = editingOutfitId != null
        Views.toggle(
            showViews = if (editMode) listOf(b.plannerEditHeader) else listOf(b.plannerTabHeader),
            hideViews = if (editMode) listOf(b.plannerTabHeader) else listOf(b.plannerEditHeader),
        )
        b.plannerBack.setOnClickListener { host.back() }

        bindSlot(b.plannerSlotOuterwear, b.plannerSlotOuterwearImage, b.plannerSlotOuterwearEmpty,
            b.plannerSlotOuterwearChip, b.plannerSlotOuterwearClear, PlannerViewModel.Slot.OUTERWEAR)
        bindSlot(b.plannerSlotTop, b.plannerSlotTopImage, b.plannerSlotTopEmpty,
            b.plannerSlotTopChip, b.plannerSlotTopClear, PlannerViewModel.Slot.TOP)
        bindSlot(b.plannerSlotBottom, b.plannerSlotBottomImage, b.plannerSlotBottomEmpty,
            b.plannerSlotBottomChip, b.plannerSlotBottomClear, PlannerViewModel.Slot.BOTTOM)
        bindSlot(b.plannerSlotShoes, b.plannerSlotShoesImage, b.plannerSlotShoesEmpty,
            b.plannerSlotShoesChip, b.plannerSlotShoesClear, PlannerViewModel.Slot.SHOES)

        b.plannerName.onTextChanged { vm.outfitName.value = it }
        b.plannerSeasonRoot.setOnClickListener { anchor ->
            RowinDropdowns.show(anchor as View, SeasonLabels(), SeasonIndex()) { index ->
                vm.outfitSeason.value = com.example.rowin_adlproject.data.Season.entries[index]
            }
        }
        b.plannerOccasionRoot.setOnClickListener { anchor ->
            RowinDropdowns.show(anchor as View, OccasionLabels(), OccasionIndex()) { index ->
                vm.outfitOccasion.value = com.example.rowin_adlproject.data.Occasion.entries[index]
            }
        }

        b.plannerGenerate.setOnClickListener {
            b.plannerError.visibility = View.GONE
            vm.generateOutfits()
        }
        b.plannerSave.setOnClickListener {
            b.plannerError.visibility = View.GONE
            vm.save(
                onSaved = {
                    if (editMode) host.back() else host.open(Screen.OUTFIT_DETAIL, it)
                },
                onError = { message ->
                    b.plannerError.text = message
                    b.plannerError.visibility = View.VISIBLE
                },
            )
        }

        vm.outfitName.collectWithViewLifecycle { name ->
            if (b.plannerName.text?.toString() != name) b.plannerName.setText(name)
        }
        vm.outfitSeason.collectWithViewLifecycle {
            b.plannerSeasonValue.text = it.displayName()
        }
        vm.outfitOccasion.collectWithViewLifecycle {
            b.plannerOccasionValue.text = it.displayName()
        }
        vm.generating.collectWithViewLifecycle { generating ->
            b.plannerGenerate.text = if (generating) getString(R.string.generating)
            else getString(R.string.generate_outfit)
            b.plannerGenerate.isEnabled = !generating
            b.plannerGenerate.alpha = if (generating) 0.5f else 1f
        }
        vm.generationNote.collectWithViewLifecycle { renderSuggestions() }
        vm.generated.collectWithViewLifecycle { renderSuggestions() }
        vm.wardrobe.collectWithViewLifecycle { renderSlots() }
        // Selection lives outside `wardrobe`; each change must re-render the slots
        // (the picker sheet writes straight into these flows).
        vm.selectedTop.collectWithViewLifecycle { renderSlots() }
        vm.selectedBottom.collectWithViewLifecycle { renderSlots() }
        vm.selectedShoes.collectWithViewLifecycle { renderSlots() }
        vm.selectedOuterwear.collectWithViewLifecycle { renderSlots() }
        vm.editingOutfit.collectWithViewLifecycle { editing ->
            b.plannerEditTitle.text =
                if (editing == null) getString(R.string.outfit_planner) else getString(R.string.edit_outfit)
        }
    }

    // ---------------------------------------------------------------- slots

    private fun bindSlot(
        slot: ConstraintLayout,
        image: ImageView,
        empty: LinearLayout,
        chip: TextView,
        clear: ImageButton,
        which: PlannerViewModel.Slot,
    ) {
        slot.setOnClickListener {
            SlotPickerSheet.forSlot(which)
                .showOnce(childFragmentManager, "slot_picker_${which.name}")
        }
        // Clickable child consumes its own taps (never opens the picker).
        clear.setOnClickListener { vm.selectInSlot(which, null) }
    }

    private fun renderSlots() {
        renderSlot(b.plannerSlotOuterwear, b.plannerSlotOuterwearImage, b.plannerSlotOuterwearEmpty,
            b.plannerSlotOuterwearChip, b.plannerSlotOuterwearClear, PlannerViewModel.Slot.OUTERWEAR)
        renderSlot(b.plannerSlotTop, b.plannerSlotTopImage, b.plannerSlotTopEmpty,
            b.plannerSlotTopChip, b.plannerSlotTopClear, PlannerViewModel.Slot.TOP)
        renderSlot(b.plannerSlotBottom, b.plannerSlotBottomImage, b.plannerSlotBottomEmpty,
            b.plannerSlotBottomChip, b.plannerSlotBottomClear, PlannerViewModel.Slot.BOTTOM)
        renderSlot(b.plannerSlotShoes, b.plannerSlotShoesImage, b.plannerSlotShoesEmpty,
            b.plannerSlotShoesChip, b.plannerSlotShoesClear, PlannerViewModel.Slot.SHOES)
    }

    private fun renderSlot(
        slot: ConstraintLayout,
        image: ImageView,
        empty: LinearLayout,
        chip: TextView,
        clear: ImageButton,
        which: PlannerViewModel.Slot,
    ) {
        val item = vm.selectionForSlot(which)?.let { id ->
            vm.wardrobe.value.firstOrNull { it.id == id }
        }
        if (item == null) {
            Views.hide(image, chip, clear)
            Views.show(empty)
            slot.background = requireContext().getDrawable(R.drawable.bg_slot_empty)
        } else {
            val bitmap = Views.decodeSampled(vm.imageFileFor(item), 700)
            if (bitmap != null) {
                image.setImageBitmap(bitmap)
            } else {
                image.setImageDrawable(null)
            }
            Views.show(image, chip, clear)
            Views.hide(empty)
            slot.background = requireContext().getDrawable(R.drawable.bg_slot_filled)
        }
    }

    // ---------------------------------------------------------- suggestions

    private fun renderSuggestions() {
        val generated = vm.generated.value
        if (generated.isEmpty()) {
            Views.hide(b.plannerSuggestionsContainer)
            b.plannerSuggestionsList.removeAllViews()
            return
        }
        Views.show(b.plannerSuggestionsContainer)
        b.plannerGenerationNote.text = vm.generationNote.value.orEmpty()
        b.plannerGenerationNote.visibility =
            if (vm.generationNote.value.isNullOrBlank()) View.GONE else View.VISIBLE
        b.plannerSuggestionsList.removeAllViews()
        generated.forEach { suggestion ->
            val row = layoutInflater.inflate(
                R.layout.item_suggestion, b.plannerSuggestionsList, false,
            ) as LinearLayout
            row.findViewById<TextView>(R.id.suggestion_name).text = suggestion.name
            row.findViewById<TextView>(R.id.suggestion_caption).text =
                "${suggestion.itemIds.size} pieces - ${suggestion.occasion.displayName()}"
            row.setOnClickListener { vm.applySuggestion(suggestion) }
            b.plannerSuggestionsList.addView(row)
        }
    }

    private fun SeasonLabels() = com.example.rowin_adlproject.data.Season.entries.map { it.displayName() }
    private fun SeasonIndex() =
        com.example.rowin_adlproject.data.Season.entries.indexOf(vm.outfitSeason.value)
    private fun OccasionLabels() = com.example.rowin_adlproject.data.Occasion.entries.map { it.displayName() }
    private fun OccasionIndex() =
        com.example.rowin_adlproject.data.Occasion.entries.indexOf(vm.outfitOccasion.value)

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    /** Item picker bottom sheet per slot (shares the planner's ViewModel). */
    class SlotPickerSheet : SheetDialogFragment(R.layout.sheet_slot_picker) {

        companion object {
            private const val ARG_SLOT = "arg_slot"

            fun forSlot(slot: PlannerViewModel.Slot): SlotPickerSheet =
                SlotPickerSheet().apply {
                    arguments = Bundle().apply { putSerializable(ARG_SLOT, slot) }
                }
        }

        override fun onSheetReady(view: View) {
            val binding = SheetSlotPickerBinding.bind(view)
            val slot = requireArguments().getSerializable(ARG_SLOT) as PlannerViewModel.Slot
            // Same ViewModelStore as the planner fragment -> the same VM.
            val vm = androidx.lifecycle.ViewModelProvider(requireParentFragment())[
                PlannerViewModel::class.java]

            binding.sheetPickerTitle.text = getString(R.string.choose_item, slot.label)
            val options = vm.wardrobeForSlot(slot)
            if (options.isEmpty()) {
                Views.show(binding.sheetPickerEmpty)
                Views.hide(binding.sheetPickerThumbs)
            } else {
                Views.hide(binding.sheetPickerEmpty)
                Views.show(binding.sheetPickerThumbs)
                val selectedId = vm.selectionForSlot(slot)
                binding.sheetPickerThumbs.adapter = ThumbAdapter(options, selectedId, vm::imageFileFor) { item ->
                    vm.selectInSlot(slot, item.id)
                    dismiss()
                }
            }
            if (selectedIdOf(vm, slot) != null) {
                Views.show(binding.sheetPickerClear)
            } else {
                Views.hide(binding.sheetPickerClear)
            }
            binding.sheetPickerClear.setOnClickListener {
                vm.selectInSlot(slot, null)
                dismiss()
            }
        }

        private fun selectedIdOf(vm: PlannerViewModel, slot: PlannerViewModel.Slot): Long? =
            vm.selectionForSlot(slot)

        private class ThumbAdapter(
            private val options: List<ClothingItemEntity>,
            private val selectedId: Long?,
            private val imageFileFor: (ClothingItemEntity) -> java.io.File?,
            private val onPick: (ClothingItemEntity) -> Unit,
        ) : androidx.recyclerview.widget.RecyclerView.Adapter<ThumbAdapter.Holder>() {

            class Holder(val root: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(root)

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
                Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_slot_thumb, parent, false))

            override fun getItemCount(): Int = options.size

            override fun onBindViewHolder(holder: Holder, position: Int) {
                val item = options[position]
                val context = holder.root.context
                holder.root.background = context.getDrawable(
                    if (item.id == selectedId) R.drawable.bg_slot_thumb_selected else R.drawable.bg_slot_thumb,
                )
                val image = holder.root.findViewById<ImageView>(R.id.slot_thumb_image)
                val name = holder.root.findViewById<TextView>(R.id.slot_thumb_name)
                val bitmap = Views.decodeSampled(imageFileFor(item), 240)
                if (bitmap != null) {
                    image.setImageBitmap(bitmap)
                    image.visibility = View.VISIBLE
                    name.visibility = View.GONE
                } else {
                    image.setImageDrawable(null)
                    name.text = item.name
                }
                holder.root.setOnClickListener { onPick(item) }
            }
        }
    }
}
