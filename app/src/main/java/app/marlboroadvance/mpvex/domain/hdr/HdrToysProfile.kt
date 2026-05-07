package app.marlboroadvance.mpvex.domain.hdr

enum class HdrToysProfile(
  val configSection: String,
  val targetPrim: String,
  val targetTrc: String,
  val shaderPaths: List<String>,
  val shaderOptions: List<Pair<String, String>> = emptyList(),
) {
  BT_2100_PQ(
    configSection = "bt.2100-pq",
    targetPrim = "bt.2020",
    targetTrc = "pq",
    shaderPaths = listOf(
      "hdr-toys/utils/clip_both.glsl",
      "hdr-toys/transfer-function/pq_inv.glsl",
      "hdr-toys/tone-mapping/astra.glsl",
      "hdr-toys/gamut-mapping/bottosson.glsl",
      "hdr-toys/transfer-function/bt1886.glsl",
    ),
    shaderOptions = listOf("auto_exposure_limit_postive" to "1.02"),
  ),
  BT_2100_HLG(
    configSection = "bt.2100-hlg",
    targetPrim = "bt.2020",
    targetTrc = "hlg",
    shaderPaths = listOf(
      "hdr-toys/utils/clip_both.glsl",
      "hdr-toys/transfer-function/hlg_inv.glsl",
      "hdr-toys/tone-mapping/astra.glsl",
      "hdr-toys/gamut-mapping/bottosson.glsl",
      "hdr-toys/transfer-function/bt1886.glsl",
    ),
  ),
  BT_2020(
    configSection = "bt.2020",
    targetPrim = "bt.2020",
    targetTrc = "bt.1886",
    shaderPaths = listOf(
      "hdr-toys/transfer-function/bt1886_inv.glsl",
      "hdr-toys/gamut-mapping/bottosson.glsl",
      "hdr-toys/transfer-function/bt1886.glsl",
    ),
  );

  val shaderOptionsValue: String
    get() = shaderOptions.joinToString(",") { (name, value) -> "$name=$value" }

  val mpvShaderPaths: List<String>
    get() = shaderPaths.map { path -> "$MPV_SHADER_PREFIX$path" }

  companion object {
    private const val MPV_SHADER_PREFIX = "~~/shaders/"

    val allShaderPaths: Set<String> = entries
      .flatMap { it.shaderPaths }
      .toSet()

    val allMpvShaderPaths: Set<String> = allShaderPaths
      .map { path -> "$MPV_SHADER_PREFIX$path" }
      .toSet()
  }
}
