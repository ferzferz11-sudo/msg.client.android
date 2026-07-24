package lavender.client.android.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import lavender.client.android.R

class InstallAgentBottomSheet : BottomSheetDialogFragment() {

    private var onInstall: ((String) -> Unit)? = null

    companion object {
        fun show(activity: FragmentActivity, onInstall: (String) -> Unit) {
            val sheet = InstallAgentBottomSheet().apply {
                this.onInstall = onInstall
            }
            sheet.show(activity.supportFragmentManager, "install_agent")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_install_agent, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val shareCodeInput = view.findViewById<EditText>(R.id.shareCodeInput)
        val installButton = view.findViewById<MaterialButton>(R.id.btnInstall)

        installButton.setOnClickListener {
            val shareCode = shareCodeInput.text.toString().trim()
            if (shareCode.isEmpty()) {
                Toast.makeText(context, getString(R.string.please_enter_share_code), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onInstall?.invoke(shareCode)
            dismiss()
        }
    }
}
