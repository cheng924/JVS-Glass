package com.example.jvsglass.ui.me;

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.jvsglass.R
import com.example.jvsglass.utils.ToastUtils

class MeFragment : Fragment() {
    private val llPersonalData: View by lazy { requireView().findViewById(R.id.ll_personal_data) }
    private val menuContainer: ViewGroup by lazy { requireView().findViewById(R.id.menu_container) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_me, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        llPersonalData.setOnClickListener {
            ToastUtils.show(requireContext(), "编辑个人资料 - 开发中")
        }

        val titles = listOf("体验改进计划", "意见反馈", "关于", "账号与安全")
        val icons = listOf(
            R.drawable.ic_plan,
            R.drawable.ic_feedback,
            R.drawable.ic_info,
            R.drawable.ic_security
        )
        val actions = listOf(
            { ToastUtils.show(requireContext(), "体验改进计划 - 开发中") },
            { ToastUtils.show(requireContext(), "意见反馈 - 开发中") },
            { ToastUtils.show(requireContext(), "关于 - 开发中") },
            { ToastUtils.show(requireContext(), "账号与安全 - 开发中") }
        )

        for (i in 0 until menuContainer.childCount) {
            val item = menuContainer.getChildAt(i)
            item.findViewById<ImageView>(R.id.iv_icon).setImageResource(icons[i])
            item.findViewById<TextView>(R.id.tv_title).text = titles[i]
            item.setOnClickListener { actions[i]() }
        }
    }
}
