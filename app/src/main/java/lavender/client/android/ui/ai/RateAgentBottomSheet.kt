package lavender.client.android.ui.ai

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import lavender.client.android.R

class RateAgentBottomSheet : BottomSheetDialogFragment() {

    private var onRate: ((Int, String) -> Unit)? = null

    companion object {
        private const val ARG_AGENT_ID = "agent_id"

        fun show(activity: FragmentActivity, agentId: String, onRate: (Int, String) -> Unit) {
            val sheet = RateAgentBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_AGENT_ID, agentId)
                }
                this.onRate = onRate
            }
            sheet.show(activity.supportFragmentManager, "rate_agent")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_rate_agent, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val ratingBar = view.findViewById<RatingBar>(R.id.ratingBar)
        val reviewInput = view.findViewById<EditText>(R.id.reviewInput)
        val submitButton = view.findViewById<MaterialButton>(R.id.btnSubmit)

        submitButton.setOnClickListener {
            val rating = ratingBar.progress
            if (rating == 0) {
                Toast.makeText(context, "Please select a rating", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val review = reviewInput.text.toString()
            onRate?.invoke(rating, review)
            dismiss()
        }
    }
}
