package com.example.rowin_adlproject.ui.home

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.rowin_adlproject.R
import com.example.rowin_adlproject.data.ClothingItemEntity
import com.example.rowin_adlproject.data.UserEntity
import com.example.rowin_adlproject.databinding.ItemHeroAvatarBinding
import com.example.rowin_adlproject.databinding.ItemRecentClothingBinding
import com.example.rowin_adlproject.databinding.ScreenHomeBinding
import com.example.rowin_adlproject.ui.components.displayName
import com.example.rowin_adlproject.ui.view.HostedFragment
import com.example.rowin_adlproject.ui.view.Screen
import com.example.rowin_adlproject.ui.view.Views

/**
 * Feature #2 - Home Dashboard: personalized greeting, live wardrobe stats,
 * quick cards, saved-outfit note, and the recently-added rail.
 */
class HomeFragment : HostedFragment(R.layout.screen_home) {

    private var _b: ScreenHomeBinding? = null
    private val b get() = _b!!

    private lateinit var vm: HomeViewModel
    private lateinit var recentAdapter: RecentAdapter
    private lateinit var avatarAdapter: HeroAvatarAdapter

    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ): android.view.View {
        _b = ScreenHomeBinding.inflate(inflater, container, false)
        return b.root
    }

    override fun onViewReady(view: android.view.View) {
        vm = androidx.lifecycle.ViewModelProvider(
            this, LoadersFactory(),
        )[HomeViewModel::class.java]

        recentAdapter = RecentAdapter(vm::imageFileFor) { item -> host.open(Screen.CLOTHING_DETAIL, item.id) }
        b.homeRecentRail.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.homeRecentRail.adapter = recentAdapter

        avatarAdapter = HeroAvatarAdapter(vm::imageFileFor)
        b.homeHeroAvatars.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        b.homeHeroAvatars.adapter = avatarAdapter

        b.homeHeroCard.setOnClickListener { host.open(Screen.CLOSET) }
        b.homeQuickPlanner.setOnClickListener { host.open(Screen.OUTFITS) }
        // Profile is already a bottom-bar tab; the second quick card goes to the
        // stylist, which otherwise lives two taps deep inside Profile.
        b.homeQuickStylist.setOnClickListener { host.open(Screen.CHAT) }
        b.homeSeeAll.setOnClickListener { host.open(Screen.CLOSET) }

        bindUser(host.rowin.user.value)
        vm.clothingCount.collectWithViewLifecycle { count ->
            b.homeHeroCount.text = "$count Items"
        }
        vm.newThisWeek.collectWithViewLifecycle { newThisWeek ->
            b.homeHeroSubtitle.text =
                if (newThisWeek > 0) {
                    "$newThisWeek new additions this week. Explore your curated collection."
                } else {
                    getString(R.string.hero_empty_subtitle)
                }
        }
        vm.recentItems.collectWithViewLifecycle { items ->
            recentAdapter.submit(items)
            avatarAdapter.submit(items.take(3))
            Views.toggle(
                showViews = if (items.isEmpty()) listOf(b.homeRecentEmpty) else listOf(b.homeRecentRail),
                hideViews = if (items.isEmpty()) listOf(b.homeRecentRail) else listOf(b.homeRecentEmpty),
            )
        }
        vm.outfitsCount.collectWithViewLifecycle { outfits ->
            if (outfits > 0) {
                b.homeSavedNote.text =
                    "You have $outfits saved ${if (outfits == 1) "outfit" else "outfits"}"
                Views.show(b.homeSavedNote)
            } else {
                Views.hide(b.homeSavedNote)
            }
        }
    }

    private fun bindUser(user: UserEntity?) {
        val firstName = user?.name?.split(" ")?.firstOrNull() ?: "there"
        b.homeGreeting.text = "${HomeViewModel.currentGreeting()}, $firstName"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }

    /** Fragment-scoped factory (needs the host user id). */
    private inner class LoadersFactory : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            HomeViewModel.Factory(requireContext(), host.userId).create(modelClass)
    }

    // -------------------------------------------------------- recent rail

    private class RecentAdapter(
        private val imageFileFor: (ClothingItemEntity) -> java.io.File?,
        private val onClick: (ClothingItemEntity) -> Unit,
    ) : RecyclerView.Adapter<RecentAdapter.Holder>() {

        private val items = mutableListOf<ClothingItemEntity>()

        fun submit(newItems: List<ClothingItemEntity>) {
            items.clear()
            items += newItems.take(10)
            notifyDataSetChanged()
        }

        class Holder(val binding: ItemRecentClothingBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemRecentClothingBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            val binding = holder.binding
            val file = imageFileFor(item)
            val bitmap = file?.let { BitmapFactory.decodeFile(it.absolutePath) }
            if (bitmap != null) {
                binding.recentImage.setImageBitmap(bitmap)
                Views.hide(binding.recentImagePlaceholder)
            } else {
                binding.recentImage.setImageDrawable(null)
                Views.show(binding.recentImagePlaceholder)
            }
            binding.recentName.text = item.name
            binding.recentCategory.text = item.category.displayName()
            binding.recentItemRoot.setOnClickListener { onClick(item) }
        }
    }

    // ------------------------------------------------- hero avatar dots

    private class HeroAvatarAdapter(
        private val imageFileFor: (ClothingItemEntity) -> java.io.File?,
    ) : RecyclerView.Adapter<HeroAvatarAdapter.Holder>() {

        private val items = mutableListOf<ClothingItemEntity>()

        fun submit(newItems: List<ClothingItemEntity>) {
            if (items == newItems) return
            items.clear()
            items += newItems
            notifyDataSetChanged()
        }

        class Holder(val binding: ItemHeroAvatarBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemHeroAvatarBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val file = imageFileFor(items[position])
            val bitmap = file?.let { BitmapFactory.decodeFile(it.absolutePath) }
            if (bitmap != null) holder.binding.heroAvatarImage.setImageBitmap(bitmap)
            else holder.binding.heroAvatarImage.setImageResource(R.drawable.ic_person_outlined)
            // Compose used spacedBy(-8.dp): overlap each dot onto the previous.
            (holder.binding.root.layoutParams as? RecyclerView.LayoutParams)?.let { params ->
                params.marginStart = if (position > 0) -(8 * holder.binding.root.context.resources
                    .displayMetrics.density).toInt() else 0
                holder.binding.root.layoutParams = params
            }
        }
    }
}
