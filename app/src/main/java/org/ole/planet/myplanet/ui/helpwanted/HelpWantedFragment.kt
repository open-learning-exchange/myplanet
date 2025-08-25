package org.ole.planet.myplanet.ui.helpwanted

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.fragment.app.Fragment
import com.google.gson.JsonObject
import com.google.gson.JsonParser.parseString
import org.ole.planet.myplanet.R
import org.ole.planet.myplanet.databinding.FragmentHelpWantedBinding
import org.ole.planet.myplanet.utilities.Constants.PREFS_NAME
import org.ole.planet.myplanet.utilities.JsonUtils.getString

class HelpWantedFragment : Fragment() {
    private var _binding: FragmentHelpWantedBinding? = null
    private val binding get() = _binding!!
    lateinit var settings: SharedPreferences
    private var manager: JsonObject? = null
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHelpWantedBinding.inflate(inflater, container, false)
        settings = requireActivity().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (settings.contains("user_admin")) manager = parseString(settings.getString("user_admin", "")).asJsonObject
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val boldName = "<b>" + getString(R.string.name_colon) + "</b>"
        val boldEmail = "<b>" + getString(R.string.email_colon) + "</b>"
        val boldPhone = "<b>" + getString(R.string.phone_number_colon) + "</b>"
        if (manager != null) {
            binding.llData.visibility = View.VISIBLE
            binding.tvName.text = Html.fromHtml(boldName + getString("name", manager), HtmlCompat.FROM_HTML_MODE_LEGACY)
            binding.tvEmail.text = Html.fromHtml(boldEmail + getString("name", manager), HtmlCompat.FROM_HTML_MODE_LEGACY)
            binding.tvPhone.text = Html.fromHtml(boldPhone + getString("phoneNumber", manager), HtmlCompat.FROM_HTML_MODE_LEGACY)
        } else {
            binding.llData.visibility = View.GONE
            binding.tvNodata.setText(R.string.no_data_available)
            binding.tvNodata.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
