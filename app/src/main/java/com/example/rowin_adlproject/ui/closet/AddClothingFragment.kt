package com.example.rowin_adlproject.ui.closet

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.rowin_adlproject.MainActivity
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.databinding.ScreenAddClothingBinding
import com.example.rowin_adlproject.ui.components.displayName
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.Loaders
import com.example.rowin_adlproject.ui.view.RowinDropdowns
import com.example.rowin_adlproject.ui.view.Screen
import com.example.rowin_adlproject.ui.view.Views
import com.example.rowin_adlproject.ui.view.onTextChanged
import kotlinx.coroutines.launch

/**
 * Features #5-#7 & #20: Add (or edit) a clothing item - 1:1 with the Stitch
 * add_clothing design: image area with Camera / Gallery pills, the
 * "Analyze with AI" panel, Item Details form, fixed "Add to Closet" bar.
 */
class AddClothingFragment : HostedFragment(R.layout.screen_add_clothing) {

    private var _b: ScreenAddClothingBinding? = null
    private val b get() = _b!!

    private var editing: ClothingItemEntity? = null
    private var initialized = false

    private val vm by lazy {
        androidx.lifecycle.ViewModelProvider(
            this, Loaders.addClothing(requireContext(), host.userId, editing),
        )[AddClothingViewModel::class.java]
    }

    // Gallery picker (feature #20 / practical #7) - photo picker on modern
    // Android, GetContent fallback elsewhere.
    private val galleryPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? -> uri?.let(vm::importFullImage) }

    private val galleryFallback = registerForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? -> uri?.let(vm::importFullImage) }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _b = ScreenAddClothingBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: View) {
        val editId = arguments?.getLong(MainActivity.ARG_ITEM_ID) ?: 0L
        if (editId > 0L) {
            // Edit mode: load the entity first (mirrors EditClothingLoader).
            viewLifecycleOwner.lifecycleScope.launch {
                val item = host.rowin.repository.getClothingById(editId)
                if (item == null) {
                    host.back()
                    return@launch
                }
                editing = item
                b.addTitle.text = getString(R.string.edit_clothing_title)
                b.addSave.text = getString(R.string.save_changes)
                initOnce()
            }
        } else {
            initOnce()
        }
    }

    private fun initOnce() {
        if (initialized) return
        initialized = true

        b.addClose.setOnClickListener { host.back() }

        // Camera result (feature #20): the in-app CameraX screen returns a file.
        // The camera is pushed by the activity's NavController, so its result is
        // posted to the activity's FragmentManager - listen there, not on children.
        parentFragmentManager.setFragmentResultListener(
            CameraResult.REQUEST_KEY, viewLifecycleOwner,
        ) { _, bundle ->
            (bundle.getString(CameraResult.EXTRA_PATH))?.let { path ->
                vm.importCameraFile(java.io.File(path))
            }
        }

        b.addCameraChip.setOnClickListener { host.open(Screen.CAMERA) }
        b.addGalleryChip.setOnClickListener { launchGallery() }

        // AI panel (feature #6): these two views had no listeners attached,
        // so tapping "Analyze with AI" did nothing at all.
        b.addAiAnalyze.setOnClickListener { vm.analyzeWithAi() }
        b.addAiApply.setOnClickListener { vm.applyAnalysis() }

        bindFormFields()
        observeVm()
    }

    private fun launchGallery() {
        if (Build.VERSION.SDK_INT >= 33) {
            galleryPicker.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        } else {
            galleryFallback.launch("image/*")
        }
    }

    // ------------------------------------------------------------ bindings

    private fun bindFormFields() {
        b.addName.onTextChanged { vm.name.value = it }
        b.addColor.onTextChanged { vm.color.value = it }
        b.addPattern.onTextChanged { vm.pattern.value = it }
        b.addStyle.onTextChanged { vm.style.value = it }
        b.addMaterial.onTextChanged { vm.material.value = it }
        b.addError.visibility = View.GONE

        b.addCategoryRoot.setOnClickListener { anchor ->
            RowinDropdowns.show(anchor as View, categoryLabels(), categoryIndex()) { index ->
                vm.category.value = categoryEntries()[index]
            }
        }
        b.addSeasonRoot.setOnClickListener { anchor ->
            RowinDropdowns.show(anchor as View, seasonLabels(), seasonIndex()) { index ->
                vm.season.value = seasonEntries()[index]
            }
        }
        b.addOccasionRoot.setOnClickListener { anchor ->
            RowinDropdowns.show(anchor as View, occasionLabels(), occasionIndex()) { index ->
                vm.occasion.value = occasionEntries()[index]
            }
        }

        b.addSave.setOnClickListener {
            b.addError.visibility = View.GONE
            vm.save(
                onSaved = { host.back() },
                onError = { message ->
                    b.addError.text = message
                    b.addError.visibility = View.VISIBLE
                },
            )
        }
    }

    private fun observeVm() {
        // Push initial VM values into the fields (edit mode prefill).
        b.addName.setText(vm.name.value)
        b.addColor.setText(vm.color.value)
        b.addPattern.setText(vm.pattern.value.orEmpty())
        b.addStyle.setText(vm.style.value.orEmpty())
        b.addMaterial.setText(vm.material.value.orEmpty())

        vm.category.collectWithViewLifecycle { b.addCategoryValue.text = it.displayName() }
        vm.season.collectWithViewLifecycle { b.addSeasonValue.text = it.displayName() }
        vm.occasion.collectWithViewLifecycle { b.addOccasionValue.text = it.displayName() }
        vm.analysisState.collectWithViewLifecycle { renderAnalysis(it) }

        // "Apply to fields" only writes into the ViewModel, so those values have
        // to be mirrored back into the EditTexts - otherwise the tap looks like
        // a no-op. The guard skips identical text so setText never fights the
        // user's cursor or feeds the watcher a value it just wrote itself.
        vm.name.collectWithViewLifecycle { value ->
            if (b.addName.text?.toString() != value) b.addName.setText(value)
        }
        vm.color.collectWithViewLifecycle { value ->
            if (b.addColor.text?.toString() != value) b.addColor.setText(value)
        }
        vm.pattern.collectWithViewLifecycle { value ->
            if (b.addPattern.text?.toString() != value) b.addPattern.setText(value)
        }
        vm.style.collectWithViewLifecycle { value ->
            if (b.addStyle.text?.toString() != value) b.addStyle.setText(value)
        }
        vm.material.collectWithViewLifecycle { value ->
            if (b.addMaterial.text?.toString() != value) b.addMaterial.setText(value)
        }

        vm.imageFileName.collectWithViewLifecycle { fileName ->
            val file = fileName?.let { vm.imageStore.fileFor(it) }
            val bitmap = Views.decodeSampled(file, 900)
            if (bitmap != null) {
                b.addImage.setImageBitmap(bitmap)
                Views.hide(b.addImagePlaceholder)
            } else {
                b.addImage.setImageDrawable(null)
                Views.show(b.addImagePlaceholder)
            }
        }
    }

    private fun renderAnalysis(state: AddClothingViewModel.AnalysisState) {
        when (state) {
            is AddClothingViewModel.AnalysisState.Idle -> Views.hide(b.addAiStatus, b.addAiApply)
            is AddClothingViewModel.AnalysisState.Analyzing -> {
                b.addAiStatus.text = getString(R.string.ai_analyzing)
                b.addAiStatus.setTextColor(requireContext().getColor(R.color.rowin_primary))
                Views.show(b.addAiStatus)
                Views.hide(b.addAiApply)
            }
            is AddClothingViewModel.AnalysisState.Done -> {
                b.addAiStatus.text = buildString {
                    append(state.attributes.colorName ?: getString(R.string.ai_color_detected))
                    append(state.attributes.pattern?.let { "$it. " } ?: "")
                    append(state.attributes.style?.let { "$it. " } ?: "")
                }
                b.addAiStatus.setTextColor(requireContext().getColor(R.color.rowin_on_surface_variant))
                Views.show(b.addAiStatus)
                b.addAiApply.text =
                    if (state.applied) getString(R.string.applied) else getString(R.string.apply_to_fields)
                b.addAiApply.isEnabled = !state.applied
                b.addAiApply.alpha = if (state.applied) 0.5f else 1f
                Views.show(b.addAiApply)
            }
            is AddClothingViewModel.AnalysisState.Failed -> {
                b.addAiStatus.text = state.message
                b.addAiStatus.setTextColor(requireContext().getColor(R.color.rowin_error))
                Views.show(b.addAiStatus)
                Views.hide(b.addAiApply)
            }
        }
        // Stay tappable without a photo so the tap can explain itself
        // ("Add a photo first") instead of silently ignoring the user.
        b.addAiAnalyze.isEnabled = state !is AddClothingViewModel.AnalysisState.Analyzing
    }

    // ----------------------------------------------------------- dropdowns

    private fun categoryEntries() = com.example.rowin_adlproject.data.Category.entries.toList()
    private fun categoryLabels() = categoryEntries().map { it.displayName() }
    private fun categoryIndex() = categoryEntries().indexOf(vm.category.value)
    private fun seasonEntries() = com.example.rowin_adlproject.data.Season.entries.toList()
    private fun seasonLabels() = seasonEntries().map { it.displayName() }
    private fun seasonIndex() = seasonEntries().indexOf(vm.season.value)
    private fun occasionEntries() = com.example.rowin_adlproject.data.Occasion.entries.toList()
    private fun occasionLabels() = occasionEntries().map { it.displayName() }
    private fun occasionIndex() = occasionEntries().indexOf(vm.occasion.value)

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}

/** Fragment-result contract shared with [CameraCaptureFragment]. */
object CameraResult {
    const val REQUEST_KEY = "camera_result"
    const val EXTRA_PATH = "camera_result_path"
}
