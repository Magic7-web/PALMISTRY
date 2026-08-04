# UTS 和 uni-app x 代码规范文档

本文档定义了 UTS 语言和 uni-app x 项目的代码规范、最佳实践和开发标准。

---

## 1. UTS 语言规范

### 1.1 类型系统规范

#### 强类型优先，避免 any

- **原则**：始终为变量、函数参数和返回值提供明确的类型注解
- **禁止**：避免使用 `any` 类型，除非绝对必要（如与第三方库集成）
- **推荐**：使用类型推断时确保类型明确

```uts
// ✅ 推荐：明确的类型注解
let userName: string = 'John'
let userAge: number = 25
let isActive: boolean = true

// ✅ 推荐：函数类型注解
function getUserInfo(id: number): UserInfo {
	return {
		id: id,
		name: 'John'
	}
}

// ❌ 不推荐：使用 any
let data: any = getData()

// ✅ 替代方案：使用具体类型或泛型
let data: UserData = getData()
```

#### 类型推断最佳实践

```uts
// ✅ 推荐：类型可以明确推断时，可以省略类型注解
const items = [1, 2, 3] // 推断为 number[]
const message = 'Hello' // 推断为 string

// ✅ 推荐：复杂类型或需要约束时，明确指定类型
const users: Array<User> = []
const config: Record<string, any> = {}
```

### 1.2 变量和函数声明规范

#### 变量声明

- 使用 `let` 或 `const`，避免使用 `var`
- 优先使用 `const`，仅在需要重新赋值时使用 `let`

```uts
// ✅ 推荐
const API_BASE_URL = 'https://api.example.com'
let currentUser: User | null = null

// ❌ 不推荐
var oldVariable = 'deprecated'
```

#### 函数声明

```uts
// ✅ 推荐：函数声明（有提升特性）
function calculateTotal(price: number, tax: number): number {
	return price + tax
}

// ✅ 推荐：箭头函数（适用于回调、匿名函数）
const formatCurrency = (amount: number): string => {
	return `¥${amount.toFixed(2)}`
}

// ✅ 推荐：对象方法简写
const apiService = {
	getUser(id: number): Promise<User> {
		// ...
	}
}
```

### 1.3 跨平台 API 调用规范

- 使用 uni-app 提供的统一 API，避免直接调用平台特定 API
- 使用条件编译处理平台差异

```uts
// ✅ 推荐：使用 uni API
uni.request({
	url: 'https://api.example.com/data',
	success: (res) => {
		console.log(res.data)
	}
})

// ✅ 推荐：条件编译处理平台差异
// #ifdef APP-ANDROID
// Android 特定代码
// #endif

// #ifdef APP-IOS
// iOS 特定代码
// #endif
```

### 1.4 类型注解和类型推断

```uts
// ✅ 接口定义
interface UserInfo {
	id: number
	name: string
	email?: string // 可选属性
}

// ✅ 类型别名
type UserID = number
type EventCallback = (data: any) => void

// ✅ 联合类型
type Status = 'pending' | 'success' | 'error'

// ✅ 泛型使用
function getData<T>(id: number): Promise<T> {
	// ...
}
```

### 1.5 作用域规则

- **全局作用域**：仅在必要时使用，避免污染全局命名空间
- **局部作用域**：优先使用函数作用域和块作用域

```uts
// ✅ 推荐：模块作用域
export const config = {
	apiUrl: 'https://api.example.com'
}

// ❌ 不推荐：全局变量
// globalVar = 'bad' // 避免在全局作用域创建变量
```

---

## 2. uni-app x 项目规范

### 2.1 目录结构规范

```
project-root/
├── App.uvue                 # 应用入口文件
├── main.uts                 # 应用主入口
├── manifest.json            # 应用配置
├── pages.json               # 页面路由配置
├── uni.scss                 # 全局样式变量
├── index.html               # HTML 模板（如需要）
├── pages/                   # 页面目录
│   └── index/
│       └── index.uvue       # 页面文件
├── components/              # 组件目录（推荐创建）
│   └── Common/
│       └── Button.uvue
├── static/                  # 静态资源
│   ├── images/
│   └── logo.png
├── utils/                   # 工具函数（推荐创建）
│   └── api.uts
├── types/                   # 类型定义（推荐创建）
│   └── index.uts
└── unpackage/               # 编译输出目录（自动生成）
```

### 2.2 文件命名规范

- **页面文件**：使用 kebab-case，如 `user-profile.uvue`
- **组件文件**：使用 PascalCase，如 `UserCard.uvue`
- **工具文件**：使用 camelCase，如 `apiUtils.uts`
- **类型定义文件**：使用 camelCase 或 kebab-case，如 `types.uts` 或 `api-types.uts`

```
pages/
├── user/
│   ├── profile.uvue         # ✅ kebab-case
│   └── settings.uvue

components/
├── UserCard.uvue            # ✅ PascalCase
└── ProductList.uvue

utils/
├── apiUtils.uts             # ✅ camelCase
└── dateHelper.uts
```

### 2.3 组件组织规范

#### 组件结构

```vue
<template>
	<!-- 模板内容 -->
</template>

<script lang="uts">
	// 脚本内容
</script>

<style>
	/* 样式内容 */
</style>
```

#### 组件导出

```uts
// ✅ 推荐：使用 export default
export default {
	name: 'UserCard',
	props: {
		userId: {
			type: Number,
			required: true
		}
	},
	data() {
		return {
			user: null
		}
	}
}
```

### 2.4 页面生命周期规范

```uts
export default {
	// ✅ 页面加载
	onLoad(options: Record<string, any>) {
		console.log('页面加载', options)
	},

	// ✅ 页面显示
	onShow() {
		console.log('页面显示')
	},

	// ✅ 页面隐藏
	onHide() {
		console.log('页面隐藏')
	},

	// ✅ 页面卸载
	onUnload() {
		console.log('页面卸载')
	}
}
```

---

## 3. 命名规范

### 3.1 变量和函数命名

- **变量/函数**：使用 `camelCase`

```uts
// ✅ 推荐
const userName = 'John'
const getUserInfo = () => {}
const isUserActive = true

// ❌ 不推荐
const user_name = 'John'      // snake_case
const GetUserInfo = () => {}  // PascalCase（用于函数）
```

### 3.2 常量命名

- **常量**：使用 `UPPER_SNAKE_CASE`

```uts
// ✅ 推荐
const API_BASE_URL = 'https://api.example.com'
const MAX_RETRY_COUNT = 3
const DEFAULT_TIMEOUT = 5000

// ❌ 不推荐
const apiBaseUrl = 'https://api.example.com'  // 非常量
const maxRetryCount = 3  // 非常量
```

### 3.3 类型和接口命名

- **类型/接口**：使用 `PascalCase`

```uts
// ✅ 推荐
interface UserInfo {
	id: number
	name: string
}

type ApiResponse<T> = {
	data: T
	code: number
}

class UserService {
	// ...
}

// ❌ 不推荐
interface userInfo {}  // camelCase
type apiResponse<T> = {}  // camelCase
```

### 3.4 文件和组件命名

- **页面文件**：kebab-case（如 `user-profile.uvue`）
- **组件文件**：PascalCase（如 `UserCard.uvue`）
- **工具文件**：camelCase（如 `apiUtils.uts`）

---

## 4. 代码风格

### 4.1 缩进

- **使用 Tab**（与现有代码保持一致）
- **Tab 大小**：建议 1 Tab = 4 空格（根据团队约定）

```uts
// ✅ 推荐：Tab 缩进
export default {
	onLoad() {
		const data = getData()
		if (data) {
			processData(data)
		}
	}
}
```

### 4.2 引号

- **优先使用单引号**

```uts
// ✅ 推荐
const message = 'Hello World'
const html = '<div class="container">Content</div>'

// ✅ 允许：字符串内包含单引号时使用双引号
const text = "It's a beautiful day"

// ❌ 不推荐：统一使用双引号
const message = "Hello World"
```

### 4.3 分号

- **统一使用分号**（与现有代码保持一致）

```uts
// ✅ 推荐
const name = 'John';
function greet() {
	console.log('Hello');
}

// ❌ 不推荐：省略分号（除非项目统一约定）
const name = 'John'
function greet() {
	console.log('Hello')
}
```

### 4.4 代码格式化工具配置建议

#### Prettier 配置（`.prettierrc`）

```json
{
	"useTabs": true,
	"tabWidth": 4,
	"singleQuote": true,
	"semi": true,
	"trailingComma": "es5",
	"arrowParens": "always",
	"printWidth": 100
}
```

#### ESLint 配置（`.eslintrc.js`）

```javascript
module.exports = {
	extends: ['@dcloudio/vue-ts-essential'],
	rules: {
		'@typescript-eslint/no-explicit-any': 'warn',
		'@typescript-eslint/explicit-function-return-type': 'off',
		'vue/multi-word-component-names': 'off'
	}
}
```

---

## 5. 条件编译规范

### 5.1 条件编译标识符使用

```uts
// ✅ 平台条件编译
// #ifdef APP-ANDROID
// Android 特定代码
uni.showToast({
	title: 'Android 提示'
})
// #endif

// #ifdef APP-IOS
// iOS 特定代码
uni.showToast({
	title: 'iOS 提示'
})
// #endif

// ✅ H5 平台
// #ifdef H5
// H5 特定代码
// #endif

// ✅ 小程序平台
// #ifdef MP-WEIXIN
// 微信小程序特定代码
// #endif

// ✅ 多平台组合
// #ifdef APP-ANDROID || APP-IOS
// App 平台通用代码
// #endif
```

### 5.2 平台差异处理原则

1. **优先使用 uni API**：尽可能使用跨平台统一的 API
2. **条件编译最小化**：仅在必要时使用条件编译
3. **统一封装**：将平台差异封装在工具函数中

```uts
// ✅ 推荐：封装平台差异
// utils/platform.uts
// #ifdef APP-ANDROID
export function showNativeToast(message: string) {
	// Android 实现
}
// #endif

// #ifdef APP-IOS
export function showNativeToast(message: string) {
	// iOS 实现
}
// #endif

// ✅ 使用统一接口
import { showNativeToast } from '@/utils/platform'
showNativeToast('消息')
```

### 5.3 条件编译最佳实践

```uts
// ✅ 推荐：在方法级别使用条件编译
export default {
	// #ifdef APP-ANDROID
	onLastPageBackPress() {
		// Android 返回键处理
	},
	// #endif

	onLoad() {
		// 通用逻辑
	}
}
```

---

## 6. 注释和文档

### 6.1 函数注释规范

```uts
/**
 * 获取用户信息
 * @param userId - 用户 ID
 * @returns 用户信息对象
 */
function getUserInfo(userId: number): UserInfo {
	// 实现代码
}

/**
 * 发送网络请求
 * @param url - 请求地址
 * @param options - 请求选项
 * @returns Promise 对象
 */
async function request(
	url: string,
	options?: RequestOptions
): Promise<ApiResponse> {
	// 实现代码
}
```

### 6.2 复杂逻辑说明

```uts
// ✅ 推荐：解释复杂算法或业务逻辑
function calculateDiscount(price: number, userLevel: string): number {
	// 根据用户等级计算折扣：
	// - VIP: 8折
	// - 普通用户: 9折
	// - 新用户: 95折
	const discountMap: Record<string, number> = {
		VIP: 0.8,
		normal: 0.9,
		new: 0.95
	}
	const discount = discountMap[userLevel] || 1
	return price * discount
}
```

### 6.3 API 接口文档规范

```uts
/**
 * 用户 API 服务
 */
class UserService {
	/**
	 * 获取用户列表
	 * @param page - 页码，从 1 开始
	 * @param pageSize - 每页数量，默认 10
	 * @returns 用户列表数据
	 */
	async getUserList(page: number, pageSize: number = 10): Promise<UserListResponse> {
		// 实现代码
	}

	/**
	 * 创建用户
	 * @param userData - 用户数据
	 * @returns 创建的用户信息
	 */
	async createUser(userData: CreateUserRequest): Promise<UserInfo> {
		// 实现代码
	}
}
```

---

## 7. Git 和版本控制

### 7.1 分支命名规范

- **主分支**：`main` 或 `master`
- **开发分支**：`develop`
- **功能分支**：`feature/功能名称`，如 `feature/user-login`
- **修复分支**：`fix/问题描述`，如 `fix/login-bug`
- **发布分支**：`release/版本号`，如 `release/v1.0.0`

```
main
├── develop
│   ├── feature/user-login
│   ├── feature/payment
│   └── fix/login-bug
└── release/v1.0.0
```

### 7.2 Commit Message 规范

使用 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
<type>(<scope>): <subject>

<body>

<footer>
```

#### Type 类型

- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 文档更新
- `style`: 代码格式调整（不影响功能）
- `refactor`: 代码重构
- `test`: 测试相关
- `chore`: 构建/工具链相关

#### 示例

```
feat(user): 添加用户登录功能

实现用户登录逻辑，包括：
- 用户名/密码验证
- Token 存储
- 自动登录

Closes #123
```

```
fix(api): 修复网络请求超时问题

将请求超时时间从 5 秒调整为 10 秒

Fixes #456
```

### 7.3 PR 审查流程

1. **创建 PR 前**：
   - 代码通过本地测试
   - 遵循代码规范
   - 提交信息清晰

2. **PR 标题**：使用与 Commit Message 相同的格式

3. **PR 描述**：
   - 说明变更内容
   - 相关 Issue 链接
   - 测试说明

4. **审查检查项**：
   - 代码是否符合规范
   - 是否有必要的测试
   - 是否更新了文档
   - 是否有安全隐患

---

## 8. 性能和最佳实践

### 8.1 布局规范

- **优先使用 flex 布局**

```vue
<template>
	<!-- ✅ 推荐：flex 布局 -->
	<view class="container">
		<view class="header">头部</view>
		<view class="content">内容</view>
		<view class="footer">底部</view>
	</view>
</template>

<style>
.container {
	display: flex;
	flex-direction: column;
	height: 100vh;
}

.header {
	flex-shrink: 0;
}

.content {
	flex: 1;
	overflow: auto;
}

.footer {
	flex-shrink: 0;
}
</style>
```

### 8.2 单位使用规范

- **响应式单位**：优先使用 `rpx`（responsive pixel）
- **固定尺寸**：使用 `px`
- **字体大小**：建议使用 `rpx`

```vue
<style>
/* ✅ 推荐：响应式布局使用 rpx */
.container {
	width: 750rpx;  /* 等于屏幕宽度 */
	padding: 20rpx;
}

.text {
	font-size: 28rpx;
	line-height: 40rpx;
}

/* ✅ 允许：固定尺寸使用 px */
.border {
	border-width: 1px;  /* 1px 边框 */
}
</style>
```

### 8.3 异步处理规范

```uts
// ✅ 推荐：使用 async/await
async function fetchUserData(userId: number): Promise<UserInfo> {
	try {
		const response = await uni.request({
			url: `https://api.example.com/users/${userId}`
		})
		return response.data as UserInfo
	} catch (error) {
		console.error('获取用户数据失败', error)
		throw error
	}
}

// ✅ 推荐：Promise 链式调用（备选方案）
function fetchUserData(userId: number): Promise<UserInfo> {
	return uni.request({
		url: `https://api.example.com/users/${userId}`
	}).then(response => {
		return response.data as UserInfo
	}).catch(error => {
		console.error('获取用户数据失败', error)
		throw error
	})
}
```

### 8.4 组件复用原则

- **单一职责**：每个组件只负责一个功能
- **可配置性**：通过 props 实现组件配置
- **可组合性**：小组件组合成复杂组件

```vue
<!-- ✅ 推荐：可复用的按钮组件 -->
<template>
	<button
		:class="['custom-button', `button-${type}`, { 'button-disabled': disabled }]"
		:disabled="disabled"
		@click="handleClick"
	>
		<slot></slot>
	</button>
</template>

<script lang="uts">
export default {
	name: 'CustomButton',
	props: {
		type: {
			type: String,
			default: 'default'  // default, primary, danger
		},
		disabled: {
			type: Boolean,
			default: false
		}
	},
	methods: {
		handleClick() {
			if (!this.disabled) {
				this.$emit('click')
			}
		}
	}
}
</script>
```

---

## 9. 示例代码

### 9.1 页面组件示例

基于项目中的 `pages/index/index.uvue`：

```vue
<template>
	<view class="container">
		<image class="logo" :src="logoPath" mode="aspectFit"></image>
		<view class="text-area">
			<text class="title">{{ title }}</text>
			<text class="subtitle">{{ subtitle }}</text>
		</view>
		<CustomButton type="primary" @click="handleButtonClick">
			点击我
		</CustomButton>
	</view>
</template>

<script lang="uts">
import CustomButton from '@/components/CustomButton.uvue'

interface PageData {
	title: string
	subtitle: string
	logoPath: string
}

export default {
	name: 'IndexPage',
	components: {
		CustomButton
	},
	data(): PageData {
		return {
			title: 'Hello',
			subtitle: '欢迎使用 uni-app x',
			logoPath: '/static/logo.png'
		}
	},
	onLoad(options: Record<string, any>) {
		console.log('页面加载', options)
		this.initPage()
	},
	methods: {
		initPage() {
			// 初始化页面数据
		},
		handleButtonClick() {
			uni.showToast({
				title: '按钮被点击',
				icon: 'success'
			})
		}
	}
}
</script>

<style>
.container {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	min-height: 100vh;
	padding: 40rpx;
}

.logo {
	height: 200rpx;
	width: 200rpx;
	margin-bottom: 40rpx;
}

.text-area {
	display: flex;
	flex-direction: column;
	align-items: center;
	margin-bottom: 60rpx;
}

.title {
	font-size: 36rpx;
	font-weight: bold;
	color: #333;
	margin-bottom: 20rpx;
}

.subtitle {
	font-size: 28rpx;
	color: #666;
}
</style>
```

### 9.2 应用入口示例

基于项目中的 `App.uvue`：

```vue
<script lang="uts">
	let firstBackTime = 0
	const BACK_PRESS_INTERVAL = 2000  // 2 秒内再次按返回键退出

	export default {
		onLaunch(options: AppLaunchOptions) {
			console.log('App Launch', options)
			this.initApp()
		},
		onShow() {
			console.log('App Show')
		},
		onHide() {
			console.log('App Hide')
		},
		// #ifdef APP-ANDROID
		onLastPageBackPress() {
			console.log('App LastPageBackPress')
			const currentTime = Date.now()
			
			if (firstBackTime === 0) {
				uni.showToast({
					title: '再按一次退出应用',
					position: 'bottom',
					icon: 'none'
				})
				firstBackTime = currentTime
				
				setTimeout(() => {
					firstBackTime = 0
				}, BACK_PRESS_INTERVAL)
			} else if (currentTime - firstBackTime < BACK_PRESS_INTERVAL) {
				firstBackTime = currentTime
				uni.exit()
			} else {
				firstBackTime = currentTime
			}
		},
		// #endif
		onExit() {
			console.log('App Exit')
		},
		methods: {
			initApp() {
				// 初始化应用配置
				// 检查更新
				// 初始化全局状态
			}
		}
	}
</script>

<style>
	/* 每个页面公共 CSS */
	.uni-row {
		flex-direction: row;
	}

	.uni-column {
		flex-direction: column;
	}

	/* 全局样式变量可在 uni.scss 中定义 */
</style>
```

### 9.3 工具函数示例

```uts
// utils/api.uts

interface RequestOptions {
	url: string
	method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
	data?: any
	header?: Record<string, string>
}

interface ApiResponse<T = any> {
	code: number
	data: T
	message: string
}

const API_BASE_URL = 'https://api.example.com'
const DEFAULT_TIMEOUT = 10000

/**
 * 统一请求封装
 */
export async function request<T = any>(options: RequestOptions): Promise<ApiResponse<T>> {
	return new Promise((resolve, reject) => {
		uni.request({
			url: `${API_BASE_URL}${options.url}`,
			method: options.method || 'GET',
			data: options.data,
			header: {
				'Content-Type': 'application/json',
				...options.header
			},
			timeout: DEFAULT_TIMEOUT,
			success: (res) => {
				if (res.statusCode === 200) {
					resolve(res.data as ApiResponse<T>)
				} else {
					reject(new Error(`请求失败: ${res.statusCode}`))
				}
			},
			fail: (error) => {
				reject(error)
			}
		})
	})
}

/**
 * GET 请求
 */
export async function get<T = any>(url: string, params?: any): Promise<ApiResponse<T>> {
	const queryString = params ? '?' + Object.keys(params)
		.map(key => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
		.join('&') : ''
	
	return request<T>({
		url: url + queryString,
		method: 'GET'
	})
}

/**
 * POST 请求
 */
export async function post<T = any>(url: string, data?: any): Promise<ApiResponse<T>> {
	return request<T>({
		url: url,
		method: 'POST',
		data: data
	})
}
```

### 9.4 类型定义示例

```uts
// types/index.uts

/**
 * 用户信息接口
 */
export interface UserInfo {
	id: number
	name: string
	email: string
	avatar?: string
	phone?: string
	createTime: string
}

/**
 * API 响应基础结构
 */
export interface ApiResponse<T = any> {
	code: number
	data: T
	message: string
	timestamp: number
}

/**
 * 分页请求参数
 */
export interface PageRequest {
	page: number
	pageSize: number
}

/**
 * 分页响应数据
 */
export interface PageResponse<T> {
	list: T[]
	total: number
	page: number
	pageSize: number
}

/**
 * 用户状态枚举
 */
export enum UserStatus {
	Active = 'active',
	Inactive = 'inactive',
	Banned = 'banned'
}
```

---

## 10. 总结

本文档涵盖了 UTS 语言和 uni-app x 项目的代码规范，包括：

- ✅ 类型系统规范和最佳实践
- ✅ 命名规范和代码风格
- ✅ 项目结构和文件组织
- ✅ 条件编译和跨平台处理
- ✅ 注释和文档规范
- ✅ Git 工作流程
- ✅ 性能优化建议
- ✅ 实际代码示例

遵循这些规范可以确保代码的一致性、可维护性和跨平台兼容性。建议团队定期回顾和更新本规范，以适应项目发展和最佳实践的演进。

---

**文档版本**：v1.0.0  
**最后更新**：2025-01-27  
**维护者**：开发团队
