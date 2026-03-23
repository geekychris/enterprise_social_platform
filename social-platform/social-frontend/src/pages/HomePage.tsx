import CreatePostForm from '../components/feed/CreatePostForm';
import FeedView from '../components/feed/FeedView';

export default function HomePage() {
  return (
    <div className="space-y-4">
      <CreatePostForm />
      <FeedView />
    </div>
  );
}
