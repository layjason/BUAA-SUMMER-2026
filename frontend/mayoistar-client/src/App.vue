<script setup lang="ts">
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { useAppStore } from '@/stores/app'

const appStore = useAppStore()

onLaunch(() => {
  console.log('App Launch')
  uni.onNetworkStatusChange((res) => {
    appStore.setNetworkStatus(res.isConnected ? 'online' : 'offline')
  })
})

onShow(() => {
  console.log('App Show')
})

onHide(() => {
  console.log('App Hide')
})
</script>
