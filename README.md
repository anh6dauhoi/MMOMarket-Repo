# MMOMarket
📖 Introduction
- MMO Market is an integrated e-commerce platform designed to function as a unified marketplace. It addresses the need for a centralized system where users can securely buy and sell digital products and services.
- The platform is built to enable Guests, Customers, Sellers, and Administrators to interact seamlessly. It provides key functionalities including product browsing, secure purchasing, comprehensive account and shop management, content sharing via blogs, robust complaint handling, and system-wide reporting.
- The application is designed to integrate with external services, utilizing Sepay for secure payment transactions (deposits and withdrawals) and Google Gateway for authentication support.

🎯 Project Objectives
- Provide distinct role-based access and functionalities for Guests, Customers, Sellers, and Administrators.
- Ensure secure authentication using email/password and Google OAuth , and secure payment processing via the Sepay API.
- Deliver a centralized e-commerce platform for browsing, searching, and purchasing digital products.
- Enable sellers to manage their own shops, including product and variant management.
- Implement a comprehensive system for managing the full transaction lifecycle, including complaints, and withdrawals.
- Foster community engagement through blogs and product reviews.

🏗️ Tech Stack

- IDE: IntelliJ
- Backend: Spring Boot (using Spring MVC, Spring Service, Spring Data JPA).
- Frontend: Thymeleaf Templates.
- Database: MySQL.
- External APIs:
- Sepay: For payment processing (Top-Up/Deposit).
- Google OAuth: For authentication.
- Gmail: For sending OTP (One-Time Password).

👥 User Roles
- Guest (Visitor): An unregistered user who can browse public content (homepage, categories, blogs) and initiate account registration.
- Customer: A registered user authorized to browse, search, and purchase products, manage their transaction history, submit complaints, top-up "Coins," write reviews, and apply to become a Seller.
- Seller: A registered user with permissions to create and manage their products and product variants, respond to customer complaints, request fund withdrawals, and view sales statistics.
- Administrator: A system manager responsible for overseeing user accounts, approving or rejecting seller registrations, managing categories, resolving escalated complaints, managing withdrawals, and monitoring system-wide reports.

🚀 Key Features
- Secure Authentication: Register (Email/Password, Google) , Login , Forgot/Change Password , and OTP verification via Gmail.
- Product & Shop Browsing: View Homepage , Categories , Product Lists , detailed Product Details , and Seller Shop Information.
- Customer Functions: Top-Up "Coins" via Sepay API , Make Payment (Buy Product) , View Transaction History , Add to Wishlist , and Manage Profile.
- Seller Management: A full Seller Registration workflow (request, admin approval, contract upload) , a Shop Dashboard with sales statistics , Manage Products , and Manage Product Variants.
- Financials: Request Withdrawal , Admin Withdrawal Management (Approval/Rejection) , and Admin Commission Management.
- Support & Complaints: Submit Complaint , real-time Chat between Customer-Seller and Customer-Admin , and Admin Complaint Resolution.
- Content & Engagement: View/Manage Blogs and Feedback/Rate Product.
- Admin Dashboard: User Management , Shop Management (incl. Flag Shop) , Category Management , and View System Statistics.

📅 Project Status
Based on the project tracking file, the following milestones have been completed or are in progress:

✅ Core Browsing & Content: Homepage, Categories, Product Details, and Blog viewing features are Done.

✅ Authentication: User Registration, Login/Logout, and OTP functionalities are Done.

✅ Seller Onboarding: The complete Seller Registration flow (request, admin review, response) is Done.

✅ Core Seller Features: Manage Products and Manage Product Variants are Done.

✅ Core Customer Features: Make Order, Feedback Product, and View Order History are Done.

✅ Financials (Core): Top-Up Request, Withdrawal Request, and Bank Info Management are Done.

⏳ In Progress (Doing): Wishlist features , Change Password/Update Profile , Make Payment , and Admin Category Management  are in progress.

⬜ To Do: Admin Shop Management (Activate/Deactivate) is "To Do".

👨‍💻 Team Members
1. Trần Văn Tuấn Anh (AnhTVT): Responsible for Authentication, Seller Registration, all Financials (Top-Up, Withdrawal), Notifications, and Complaint Management.
2. Đỗ Thúy Anh (AnhDTT): Responsible for Public Browsing (Homepage, Products, Categories), Customer Blog features, and Wishlist.
3. Phí Quang Duy (DuyPQ): Responsible for the Admin Dashboard (User, Shop, Category Management), System Statistics, Commission settings, and Profile Updates.
4. Hoàng Xuân Hưng (HungHX): Responsible for Seller Product/Variant Management, Customer Order/Payment flow, the Chat System, and Seller-side Statistics/Complaints.

