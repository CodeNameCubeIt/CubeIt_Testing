package cz.cubeit.cubeit

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import kotlinx.android.synthetic.main.fragment_bug_report.view.*
import android.content.Intent
import android.net.Uri


class Fragment_Bug_report : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view:View = inflater.inflate(R.layout.fragment_bug_report, container, false)

        ArrayAdapter.createFromResource(
                view.context,
                R.array.bug_report,
                R.layout.spinner_inbox_item
        ).also { adapter ->
            // Specify the layout to use when the list of choices appears
            adapter.setDropDownViewResource(R.layout.spinner_inbox_item)
            // Apply the adapter to the spinner
            view.spinnerBugReport.adapter = adapter
        }

        view.imageViewSend.setOnClickListener {
            view.editTextShortDescription.text.toString().replace(" ","")
            view.editTextDetailedMessage.text.toString().replace(" ","")
            if(view.spinnerBugReport.selectedItem.toString().isNotEmpty()){
                view.spinnerBugReport.setBackgroundResource(0)
                if(view.editTextShortDescription.text.toString()!=""){
                    view.editTextShortDescription.setBackgroundResource(R.drawable.bg_basic_logincolor_dark_press)
                    if(view.editTextDetailedMessage.text.toString()!=""){
                        view.editTextDetailedMessage.setBackgroundResource(R.drawable.bg_basic_logincolor_dark_press)

                        val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.fromParts(
                                "mailto", "admin@gmail.com", null))
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, view.spinnerBugReport.selectedItem.toString())
                        emailIntent.putExtra(Intent.EXTRA_TEXT, view.editTextShortDescription.text.toString() +"\n\n" + view.editTextDetailedMessage.text.toString())
                        startActivity(Intent.createChooser(emailIntent, "Send report..."))
                    }else{
                        view.editTextDetailedMessage.setBackgroundResource(R.drawable.bg_transparent_pressed_red)
                        Toast.makeText(view.context, "This field is required!", Toast.LENGTH_SHORT).show()
                    }
                }else{
                    view.editTextShortDescription.setBackgroundResource(R.drawable.bg_transparent_pressed_red)
                    Toast.makeText(view.context, "This field is required!", Toast.LENGTH_SHORT).show()
                }
            }else{
                view.spinnerBugReport.setBackgroundResource(R.drawable.bg_transparent_pressed_red)
                Toast.makeText(view.context, "This field is required!", Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }
}
