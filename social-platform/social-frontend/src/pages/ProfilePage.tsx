import { useParams } from 'react-router-dom';
import UserProfile from '../components/user/UserProfile';
import FeedView from '../components/feed/FeedView';

export default function ProfilePage() {
  const { id } = useParams<{ id: string }>();

  if (!id) {
    return (
      <div className="card p-8 text-center text-gray-400">Invalid user</div>
    );
  }

  return (
    <div className="space-y-4">
      <UserProfile userId={id} />
      <h2 className="text-lg font-semibold text-gray-900">Posts</h2>
      <FeedView />
    </div>
  );
}
