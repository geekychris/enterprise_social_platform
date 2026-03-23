import { useSearchParams } from 'react-router-dom';
import SearchResults from '../components/search/SearchResults';
import SearchBar from '../components/search/SearchBar';
import { useNavigate } from 'react-router-dom';

export default function SearchPage() {
  const [params] = useSearchParams();
  const query = params.get('q') ?? '';
  const type = params.get('type') ?? undefined;
  const navigate = useNavigate();

  return (
    <div className="space-y-4">
      <div className="card p-4">
        <SearchBar
          initialValue={query}
          onSearch={(q) => navigate(`/search?q=${encodeURIComponent(q)}`)}
        />
      </div>

      {/* Type filter */}
      <div className="flex gap-2">
        {['All', 'USER', 'TEAM', 'GROUP', 'PAGE', 'POST'].map((t) => {
          const isActive = t === 'All' ? !type : type === t;
          return (
            <button
              key={t}
              onClick={() => {
                const p = new URLSearchParams({ q: query });
                if (t !== 'All') p.set('type', t);
                navigate(`/search?${p.toString()}`);
              }}
              className={`px-3 py-1.5 rounded-full text-sm font-medium transition-colors ${
                isActive
                  ? 'bg-primary-500 text-white'
                  : 'bg-white text-gray-600 border border-gray-200 hover:bg-gray-50'
              }`}
            >
              {t === 'All' ? 'All' : t.charAt(0) + t.slice(1).toLowerCase() + 's'}
            </button>
          );
        })}
      </div>

      <SearchResults query={query} type={type} />
    </div>
  );
}
