import { Suspense } from 'react';
import { createBrowserRouter, RouterProvider, Navigate, useParams } from 'react-router-dom';
import { AppLayout, MemberLayout, TrainerLayout } from './components/layout';
import { ProtectedRoute } from './components/ProtectedRoute';
import { RouteFallback } from './components/ui/RouteFallback';
import { LoginPage, SetPasswordPage, ForgotPasswordPage } from './features/auth';
import { lazyNamed } from './utils/lazyNamed';
import { useAuthStore } from './stores/authStore';

// Code-split by area (AUDIT-mobile.md "Prestazioni"): a socio only ever needs /me, a trainer
// only /trainer — bundling every staff page, plus both of those areas, into the one chunk every
// visitor downloads (including at /login, before we even know the role) was pure waste. Each
// lazy() below becomes its own chunk, fetched the first time its route is actually visited;
// ProtectedRoute decides which area a session can reach anyway, so no area loads before its
// role is confirmed. The three layout shells (AppLayout/MemberLayout/TrainerLayout) and the
// pre-login pages stay eagerly bundled — they're on the critical path for every visitor.
const DashboardPage = lazyNamed(() => import('./features/dashboard'), 'DashboardPage');
const MembersPage = lazyNamed(() => import('./features/members'), 'MembersPage');
const MemberDetailPage = lazyNamed(() => import('./features/members'), 'MemberDetailPage');
const MemberFormPage = lazyNamed(() => import('./features/members'), 'MemberFormPage');
const SubscriptionsPage = lazyNamed(() => import('./features/subscriptions'), 'SubscriptionsPage');
const LeadsPage = lazyNamed(() => import('./features/leads'), 'LeadsPage');
const FederationsPage = lazyNamed(() => import('./features/fees'), 'FederationsPage');
const EnrollmentWizardPage = lazyNamed(() => import('./features/enrollment'), 'EnrollmentWizardPage');
const PaymentStepStandalonePage = lazyNamed(() => import('./features/enrollment'), 'PaymentStepStandalonePage');
const SubscriptionTypesPage = lazyNamed(() => import('./features/lookups'), 'SubscriptionTypesPage');
const UsersPage = lazyNamed(() => import('./features/users'), 'UsersPage');
const GymsPage = lazyNamed(() => import('./features/gyms'), 'GymsPage');
const MyGymPage = lazyNamed(() => import('./features/gyms'), 'MyGymPage');
const TodayCheckInsPage = lazyNamed(() => import('./features/checkins'), 'TodayCheckInsPage');
const CheckInScannerPage = lazyNamed(() => import('./features/qr'), 'CheckInScannerPage');
const MyQrPage = lazyNamed(() => import('./features/qr'), 'MyQrPage');
const CoursesPage = lazyNamed(() => import('./features/courses'), 'CoursesPage');
const CourseFormPage = lazyNamed(() => import('./features/courses'), 'CourseFormPage');
const PackagesPage = lazyNamed(() => import('./features/packages'), 'PackagesPage');
const PackageFormPage = lazyNamed(() => import('./features/packages'), 'PackageFormPage');
const TrainerCoursesPage = lazyNamed(() => import('./features/trainer'), 'TrainerCoursesPage');
const CourseAttendancePage = lazyNamed(() => import('./features/trainer'), 'CourseAttendancePage');
const ChangePasswordPage = lazyNamed(() => import('./features/member'), 'ChangePasswordPage');
const MyProfilePage = lazyNamed(() => import('./features/member'), 'MyProfilePage');
const MySubscriptionsPage = lazyNamed(() => import('./features/member'), 'MySubscriptionsPage');
const MyPaymentsPage = lazyNamed(() => import('./features/member'), 'MyPaymentsPage');
const MyCheckInPage = lazyNamed(() => import('./features/member'), 'MyCheckInPage');
const MyCoursesPage = lazyNamed(() => import('./features/member'), 'MyCoursesPage');
const MyDocumentsPage = lazyNamed(() => import('./features/member'), 'MyDocumentsPage');
const MyFriendsPage = lazyNamed(() => import('./features/member'), 'MyFriendsPage');

/** Wraps a lazy page in the shared route-loading fallback — one place to change the spinner. */
function Lazy({ children }: { children: React.ReactNode }) {
  return <Suspense fallback={<RouteFallback />}>{children}</Suspense>;
}

/** Redirects to the correct landing page based on the authenticated user's role. */
function RoleBasedRedirect() {
  const role = useAuthStore((s) => s.user?.role);
  if (role === 'ADMIN') {
    return <Navigate to="/users" replace />;
  }
  return <Navigate to="/dashboard" replace />;
}

/** Bookmark-protecting redirect for the old "new subscription" route → wizard from step 2. */
function RedirectToMemberEnroll() {
  const { memberId } = useParams<{ memberId: string }>();
  return <Navigate to={`/members/${memberId}/enroll`} replace />;
}

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/set-password', element: <SetPasswordPage /> },
  { path: '/forgot-password', element: <ForgotPasswordPage /> },
  {
    path: '/change-password',
    element: (
      <ProtectedRoute>
        <Lazy><ChangePasswordPage /></Lazy>
      </ProtectedRoute>
    ),
  },
  {
    path: '/',
    element: (
      <ProtectedRoute allowedRoles={['ADMIN', 'STAFF', 'GYM_MANAGER']}>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <RoleBasedRedirect /> },
      {
        path: 'dashboard',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><DashboardPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'members',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><MembersPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'members/new',
        element: <Navigate to="/enroll" replace />,
      },
      {
        path: 'members/:id',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><MemberDetailPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'members/:id/edit',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><MemberFormPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'members/:memberId/subscriptions/new',
        element: <RedirectToMemberEnroll />,
      },
      {
        path: 'members/:memberId/subscriptions/:subId/payments/new',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><PaymentStepStandalonePage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'enroll',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><EnrollmentWizardPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'members/:memberId/enroll',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><EnrollmentWizardPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'leads',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><LeadsPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'fees',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><FederationsPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'subscription-types',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><SubscriptionTypesPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'subscriptions',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><SubscriptionsPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'courses',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><CoursesPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'courses/new',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><CourseFormPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'courses/:id/edit',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><CourseFormPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'packages',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><PackagesPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'packages/new',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><PackageFormPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'packages/:id/edit',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><PackageFormPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'check-ins',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><TodayCheckInsPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'check-ins/scan',
        element: (
          <ProtectedRoute allowedRoles={['STAFF', 'GYM_MANAGER']}>
            <Lazy><CheckInScannerPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'users',
        element: (
          <ProtectedRoute allowedRoles={['ADMIN', 'GYM_MANAGER']}>
            <Lazy><UsersPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'users/trainers',
        element: (
          <ProtectedRoute allowedRoles={['GYM_MANAGER']}>
            <Lazy><UsersPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'gyms',
        element: (
          <ProtectedRoute allowedRoles={['ADMIN']}>
            <Lazy><GymsPage /></Lazy>
          </ProtectedRoute>
        ),
      },
      {
        path: 'my-gym',
        element: (
          <ProtectedRoute allowedRoles={['GYM_MANAGER']}>
            <Lazy><MyGymPage /></Lazy>
          </ProtectedRoute>
        ),
      },
    ],
  },
  {
    path: '/trainer',
    element: (
      <ProtectedRoute allowedRoles={['TRAINER']}>
        <TrainerLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <Lazy><TrainerCoursesPage /></Lazy> },
      { path: 'courses/:id/attendance', element: <Lazy><CourseAttendancePage /></Lazy> },
    ],
  },
  {
    path: '/me',
    element: (
      <ProtectedRoute allowedRoles={['MEMBER']}>
        <MemberLayout />
      </ProtectedRoute>
    ),
    children: [
      { index: true, element: <Lazy><MyProfilePage /></Lazy> },
      { path: 'check-ins', element: <Lazy><MyCheckInPage /></Lazy> },
      { path: 'qr', element: <Lazy><MyQrPage /></Lazy> },
      { path: 'subscriptions', element: <Lazy><MySubscriptionsPage /></Lazy> },
      { path: 'courses', element: <Lazy><MyCoursesPage /></Lazy> },
      { path: 'payments', element: <Lazy><MyPaymentsPage /></Lazy> },
      { path: 'documents', element: <Lazy><MyDocumentsPage /></Lazy> },
      { path: 'friends', element: <Lazy><MyFriendsPage /></Lazy> },
    ],
  },
]);

export default function App() {
  return <RouterProvider router={router} />;
}
