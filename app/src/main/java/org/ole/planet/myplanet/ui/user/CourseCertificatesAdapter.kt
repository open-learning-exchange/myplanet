package org.ole.planet.myplanet.ui.user

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.ole.planet.myplanet.databinding.ItemCourseCertificateBinding
import org.ole.planet.myplanet.model.gamification.CourseCertificate
import org.ole.planet.myplanet.utils.DiffUtils

class CourseCertificatesAdapter(
    private val onCertificateClick: (CourseCertificate) -> Unit
) : ListAdapter<CourseCertificate, CourseCertificatesAdapter.CertificateViewHolder>(
    DiffUtils.itemCallback(
        areItemsTheSame = { oldItem, newItem -> oldItem.certificateId == newItem.certificateId },
        areContentsTheSame = { oldItem, newItem -> oldItem == newItem }
    )
) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CertificateViewHolder {
        val binding = ItemCourseCertificateBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CertificateViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CertificateViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CertificateViewHolder(private val binding: ItemCourseCertificateBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(certificate: CourseCertificate) {
            binding.tvCertCourseTitle.text = certificate.courseTitle
            binding.tvCertDate.text = certificate.completionDate

            binding.btnViewCertificate.setOnClickListener {
                onCertificateClick(certificate)
            }
            binding.cardCertificate.setOnClickListener {
                onCertificateClick(certificate)
            }
        }
    }
}
