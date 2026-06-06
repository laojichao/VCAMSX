package com.wangyiheng.vcamsx.components

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * 设置行 Composable 组件，显示标签文本和开关切换按钮。
 *
 * 切换开关时会自动显示 Toast 提示当前状态。
 *
 * @param label 设置项标签文本
 * @param checkedState 开关状态的可变状态对象
 * @param onCheckedChange 开关状态变更回调
 * @param context 用于显示 Toast 的上下文
 */
@Composable
fun SettingRow(
    label: String,
    checkedState: MutableState<Boolean>,
    onCheckedChange: (Boolean) -> Unit,
    context: Context
) {
    Row(verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        Switch(
            checked = checkedState.value,
            onCheckedChange = {
                checkedState.value = it
                onCheckedChange(it)
                Toast.makeText(context, if (it) "$label 打开" else "$label 关闭", Toast.LENGTH_SHORT).show()
            }
        )
    }
}