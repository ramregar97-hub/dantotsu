package ani.dantotsu.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ani.dantotsu.BottomSheetDialogFragment
import ani.dantotsu.R
import ani.dantotsu.databinding.BottomSheetDiscordRpcBinding
import ani.dantotsu.settings.saving.PrefManager
import ani.dantotsu.settings.saving.PrefName

class DiscordDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetDiscordRpcBinding? = null
    private val binding get() = _binding!!

    private var isManga = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDiscordRpcBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updatePreview()

        when (PrefManager.getCustomVal("discord_mode", "dantotsu")) {
            "nothing" -> binding.radioNothing.isChecked = true
            "dantotsu" -> binding.radioDantotsu.isChecked = true
            "anilist" -> binding.radioAnilist.isChecked = true
            else -> binding.radioAnilist.isChecked = true
        }

        binding.togglePreviewType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                isManga = checkedId == binding.buttonMangaPreview.id
                updatePreview()
            }
        }

        binding.showIcon.isChecked = PrefManager.getVal(PrefName.ShowAniListIcon)
        binding.showIcon.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ShowAniListIcon, isChecked)
            updatePreview()
        }
        binding.anilistLinkPreview.text =
            getString(R.string.anilist_link, PrefManager.getVal<String>(PrefName.AnilistUserName))

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.radioNothing.id -> "nothing"
                binding.radioDantotsu.id -> "dantotsu"
                binding.radioAnilist.id -> "anilist"
                else -> "dantotsu"
            }
            PrefManager.setCustomVal("discord_mode", mode)
            updatePreview()
        }
    }

    private fun updatePreview() {
        // Set the icon
        if (binding.showIcon.isChecked) {
            binding.previewIcon.setImageResource(R.drawable.ic_anilist)
        } else {
            binding.previewIcon.setImageResource(R.drawable.ic_dantotsu_round)
        }

        if (isManga) {
            binding.previewHeader.text = "Reading To Not Die"
            binding.previewTitle.text = "Chapter 24"
            binding.previewEpisode.text = "Chapter 24/??"
            binding.animeProgressContainer.visibility = View.GONE
            binding.mangaProgressContainer.visibility = View.VISIBLE
        } else {
            binding.previewHeader.text = "Watching One-Punch Man Season 3"
            binding.previewTitle.text = "Episode 1: Strategy Meeting"
            binding.previewEpisode.text = "Episode : 1/??"
            binding.animeProgressContainer.visibility = View.VISIBLE
            binding.mangaProgressContainer.visibility = View.GONE
        }

        // Set the buttons
        when (PrefManager.getCustomVal("discord_mode", "dantotsu")) {
            "nothing" -> {
                binding.previewButton1.visibility = View.GONE
                binding.previewButton2.visibility = View.GONE
            }
            "dantotsu" -> {
                binding.previewButton1.visibility = View.VISIBLE
                binding.previewButton2.visibility = View.GONE
                binding.previewButton1.text = if(isManga) getString(R.string.read_on_dantotsu) else getString(R.string.stream_on_dantotsu)
            }
            "anilist" -> {
                binding.previewButton1.visibility = View.VISIBLE
                binding.previewButton2.visibility = View.VISIBLE
                binding.previewButton1.text = if(isManga) getString(R.string.view_manga) else getString(R.string.view_my_anilist)
                binding.previewButton2.text = if(isManga) getString(R.string.read_on_dantotsu) else getString(R.string.stream_on_dantotsu)
            }
        }
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}