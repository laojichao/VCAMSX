import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.dp

/**
 * 自适应流式布局 Composable 组件，子元素超出宽度时自动换行。
 *
 * @param modifier 应用于布局的修饰符
 * @param horizontalGap 子元素之间的水平间距，单位 dp，默认 8
 * @param verticalGap 行与行之间的垂直间距，单位 dp，默认 8
 * @param content 子元素内容
 */
@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalGap: Int = 8, // 水平间距
    verticalGap: Int = 8, // 垂直间距
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->

        val horizontalGapPx = horizontalGap.dp.toPx().toInt()
        val verticalGapPx = verticalGap.dp.toPx().toInt()

        val rows = mutableListOf<List<Placeable>>()
        var rowWidth = 0
        var rowHeight = 0
        var row = mutableListOf<Placeable>()

        measurables.forEach { measurable ->
            val placeable = measurable.measure(constraints)

            if (rowWidth + placeable.width > constraints.maxWidth) {
                rows.add(row)
                rowWidth = 0
                rowHeight += placeable.height + verticalGapPx
                row = mutableListOf()
            }

            row.add(placeable)
            rowWidth += placeable.width + horizontalGapPx
        }
        rows.add(row)

        val width = constraints.maxWidth
        val height = rowHeight

        layout(width, height) {
            var yPosition = 0

            rows.forEach { row ->
                var xPosition = 0

                row.forEach { placeable ->
                    placeable.placeRelative(x = xPosition, y = yPosition)
                    xPosition += placeable.width + horizontalGapPx
                }

                yPosition += row.first().height + verticalGapPx
            }
        }
    }
}
