export default function AboutPage() {
  return (
    <div className="space-y-6">
      {/* Platform About */}
      <div className="card overflow-hidden">
        <div className="h-24 bg-gradient-to-r from-primary-500 to-primary-600" />
        <div className="p-6 -mt-8">
          <div className="w-14 h-14 bg-primary-500 text-white rounded-2xl flex items-center justify-center text-2xl font-bold border-4 border-white shadow mb-4">
            S
          </div>
          <h1 className="text-2xl font-bold text-gray-900">
            Enterprise Social
          </h1>
          <p className="text-sm text-gray-500 mt-2 leading-relaxed">
            A social platform for teams, groups, and communities. Share posts,
            exchange messages, join groups and pages, and stay connected with
            your colleagues and friends.
          </p>
        </div>
      </div>
    </div>
  );
}
