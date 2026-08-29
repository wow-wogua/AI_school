<!-- 校园剪影装饰条：旗杆+教学楼+树，页尾前的学校天际线 -->
<template>
  <svg class="skyline" viewBox="0 0 340 56" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
    <!-- 旗杆与旗 -->
    <rect x="169" y="4" width="2" height="34" rx="1" fill="#C7D3E8" />
    <path d="M171 5 L187 10 L171 15 Z" fill="#EA580C" opacity=".55" />
    <!-- 主教学楼 -->
    <rect x="132" y="20" width="76" height="18" rx="2" fill="#D6DFF0" />
    <g fill="#F4F6FB">
      <rect x="139" y="24" width="8" height="6" rx="1" /><rect x="152" y="24" width="8" height="6" rx="1" />
      <rect x="165" y="24" width="8" height="6" rx="1" /><rect x="178" y="24" width="8" height="6" rx="1" />
      <rect x="191" y="24" width="8" height="6" rx="1" />
    </g>
    <rect x="162" y="32" width="16" height="6" rx="3" fill="#F4F6FB" />
    <!-- 左副楼 + 树 -->
    <rect x="98" y="28" width="28" height="10" rx="2" fill="#E2E8F6" />
    <circle cx="42" cy="26" r="12" fill="#DCE9DE" />
    <circle cx="58" cy="32" r="8" fill="#E2E8F6" />
    <rect x="41" y="34" width="2.5" height="6" rx="1" fill="#C7D3E8" />
    <!-- 右侧副楼 + 树 -->
    <rect x="226" y="30" width="52" height="8" rx="2" fill="#E2E8F6" />
    <circle cx="298" cy="28" r="10" fill="#DCE9DE" />
    <rect x="297" y="34" width="2.5" height="6" rx="1" fill="#C7D3E8" />
    <!-- 地平虚线 -->
    <line x1="22" y1="40" x2="318" y2="40" stroke="#D9E1F0" stroke-width="1" stroke-dasharray="5 5" />
  </svg>
</template>

<style scoped>
.skyline { display: block; width: 68%; max-width: 300px; margin: 20px auto -6px; }
</style>
