# 天工开物 · 测试用：一键解锁《乃粒》全卷条目
#
# 用法：/function tiangongkaiwu:unlock_naili_all
#   （需要 OP / 单人存档开启作弊；@s = 执行该命令的玩家）
#
# 原理：Modonomicon 条目的解锁条件是 advancement，
#       这里把 data/tiangongkaiwu/advancement/unlock/ 下的 9 个进度全部授予。
#       总论 naili_zonglun 本就无锁，故不在列表内。
#
# 重置解锁（重测解锁流程时用）：/modonomicon reset tiangongkaiwu:naili
#
# ⚠️ 每新增一条带锁的条目，记得往这里补一行。

advancement grant @s only tiangongkaiwu:unlock/naili_do
advancement grant @s only tiangongkaiwu:unlock/naili_dao_yi
advancement grant @s only tiangongkaiwu:unlock/naili_dao_gong
advancement grant @s only tiangongkaiwu:unlock/naili_dao_zai
advancement grant @s only tiangongkaiwu:unlock/naili_shuili
advancement grant @s only tiangongkaiwu:unlock/naili_mai
advancement grant @s only tiangongkaiwu:unlock/naili_shuji
advancement grant @s only tiangongkaiwu:unlock/naili_ma
advancement grant @s only tiangongkaiwu:unlock/naili_shu

tellraw @s {"text":"[天工开物] 乃粒全卷条目已解锁。","color":"gold"}
