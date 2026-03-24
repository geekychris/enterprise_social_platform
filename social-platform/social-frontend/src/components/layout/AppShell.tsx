import Header from './Header';
import Sidebar from './Sidebar';
import RightPanel from './RightPanel';

export default function AppShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-gray-50">
      <Header />
      <Sidebar />
      <main className="pt-20 lg:pl-60 xl:pr-72">
        <div className="max-w-2xl mx-auto px-4 py-6">{children}</div>
      </main>
      <RightPanel />
    </div>
  );
}
