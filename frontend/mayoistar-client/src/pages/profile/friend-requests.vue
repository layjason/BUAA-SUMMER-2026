<script setup lang="ts">
/**
 * 兼容旧路由：重定向到社交模块好友申请页
 */
import { onLoad } from '@dcloudio/uni-app'

onLoad(() => {
  uni.redirectTo({ url: '/pages/social/friend-requests' })
})
</script>
