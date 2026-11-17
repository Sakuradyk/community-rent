<template>
  <div class="auth-shell">
    <!-- 左：品牌区。答辩投影时这一屏就是"第一印象"，所以放价值主张与三条硬指标，
         不放营销话术——评委会问的正是"你的系统凭什么" -->
    <section class="brand-pane">
      <div class="brand-inner">
        <router-link to="/" class="logo">
          <span class="logo-mark" aria-hidden="true">邻</span>
          <span class="logo-text">邻里租</span>
        </router-link>

        <h1 class="headline">
          邻居家的电钻<br />
          不用买，租三天
        </h1>
        <p class="lead">
          小区里的闲置物品按天租用。押金、逾期、损耗全部算成账面数字，
          谁的钱、怎么扣，一眼看得清。
        </p>

        <ul class="points">
          <li>
            <span class="dot" aria-hidden="true"></span>
            <b>同一时段只租给一个人</b>
            <span class="point-desc">预检 + 分布式锁 + 唯一索引三道防线</span>
          </li>
          <li>
            <span class="dot" aria-hidden="true"></span>
            <b>结算不用找管理员</b>
            <span class="point-desc">租金、逾期、损耗按策略表自动算出应退金额</span>
          </li>
          <li>
            <span class="dot" aria-hidden="true"></span>
            <b>每条推荐都有理由</b>
            <span class="point-desc">"同小区 + 你借过同类" 这类说明，不是黑盒排序</span>
          </li>
        </ul>

        <p class="footnote">本科毕业设计演示环境 · 模拟支付，不涉及真实资金</p>
      </div>
    </section>

    <!-- 右：表单区。宽度锁在 400px，保证三个页面（登录/注册/找回密码）表单节奏一致 -->
    <section class="form-pane">
      <div class="form-card">
        <slot />
      </div>
    </section>
  </div>
</template>

<script setup>
// 纯展示组件：不含任何业务逻辑，也不发请求
</script>

<style scoped lang="scss">
@use '@/assets/styles/variables.scss' as *;

.auth-shell {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: 100%;
}

// ---------------- 左：品牌区 ----------------
.brand-pane {
  position: relative;
  overflow: hidden;
  display: flex;
  align-items: center;
  padding: $space-12 $space-8;
  // 暖橙渐变：左侧更浅，右侧靠近表单略深，视觉上把视线"推"向表单
  background: linear-gradient(150deg, #fdf1e6 0%, #fbe0c9 55%, #f8cda8 100%);

  // 两个纯 CSS 光斑，替代插画素材（本项目不引插画库，见 §2.1 禁新依赖）
  &::before,
  &::after {
    content: '';
    position: absolute;
    border-radius: 50%;
    filter: blur(2px);
    pointer-events: none;
  }

  &::before {
    width: 320px;
    height: 320px;
    right: -90px;
    top: -70px;
    background: radial-gradient(circle, rgba(249, 115, 22, 0.22), rgba(249, 115, 22, 0));
  }

  &::after {
    width: 260px;
    height: 260px;
    left: -80px;
    bottom: -90px;
    background: radial-gradient(circle, rgba(180, 83, 9, 0.16), rgba(180, 83, 9, 0));
  }
}

.brand-inner {
  position: relative; // 压在光斑之上
  max-width: 460px;
  margin-left: auto;
  margin-right: auto;
}

.logo {
  display: inline-flex;
  align-items: center;
  gap: $space-2;
  margin-bottom: $space-8;
}

.logo-mark {
  width: 34px;
  height: 34px;
  border-radius: $radius-sm;
  background: $color-primary;
  color: #fff;
  font-weight: $font-weight-bold;
  font-size: $font-size-md;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 12px rgba(234, 88, 12, 0.28);
}

.logo-text {
  font-size: $font-size-lg;
  font-weight: $font-weight-bold;
  color: #7c2d12;
  letter-spacing: 0.5px;
}

.headline {
  font-size: 34px;
  font-weight: $font-weight-bold;
  line-height: 1.25;
  color: #6b2c0e;
  margin-bottom: $space-4;
}

.lead {
  margin: 0 0 $space-8;
  font-size: $font-size-md;
  line-height: 1.7;
  color: #8a4a24;
}

.points {
  list-style: none;
  margin: 0 0 $space-8;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: $space-3;

  li {
    display: grid;
    grid-template-columns: 8px 1fr;
    column-gap: $space-3;
    row-gap: 2px;
    align-items: baseline;
    font-size: $font-size-sm;
    color: #6b2c0e;
  }

  b { font-weight: $font-weight-medium; }

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: $color-primary;
    transform: translateY(-1px);
  }

  .point-desc {
    grid-column: 2;
    font-size: $font-size-xs;
    color: #9a6b47;
  }
}

.footnote {
  margin: 0;
  font-size: $font-size-xs;
  color: #a97c58;
}

// ---------------- 右：表单区 ----------------
.form-pane {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: $space-8 $space-6;
  background: $color-bg;
}

.form-card {
  width: 100%;
  max-width: 400px;
  background: $color-surface;
  border-radius: $radius-lg;
  padding: $space-8 $space-6;
  border: 1px solid $color-border;
  box-shadow: $shadow-base;
}

// ---------------- 响应式：窄屏改为上下堆叠，品牌区缩成一条说明 ----------------
@media (max-width: $auth-split-min-width) {
  .auth-shell {
    grid-template-columns: 1fr;
  }

  .brand-pane {
    padding: $space-6 $space-4 $space-8;

    &::before,
    &::after { display: none; }
  }

  .brand-inner {
    max-width: none;
  }

  .headline {
    font-size: $font-size-lg;
    margin-bottom: $space-2;
  }

  .lead,
  .points,
  .footnote {
    display: none; // 窄屏只保留品牌与一句标题，别把表单挤出首屏
  }

  .logo { margin-bottom: $space-3; }

  .form-pane {
    align-items: flex-start;
    padding: 0 $space-4 $space-8;
  }

  .form-card {
    margin-top: -$space-6; // 轻微叠上来，视觉上仍是一个整块
  }
}
</style>
