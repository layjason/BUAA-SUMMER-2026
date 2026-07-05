<script setup lang="ts">
/**
 * 兼容旧路由：重定向到社交模块黑名单页
 */
import { onLoad } from '@dcloudio/uni-app'

onLoad(() => {
  uni.redirectTo({ url: '/pages/social/blacklist' })
})
</script>
