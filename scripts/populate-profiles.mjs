#!/usr/bin/env node
/**
 * Populate all users with realistic enterprise profile data.
 * Usage: node scripts/populate-profiles.mjs [BASE_URL]
 */

const BASE = process.argv[2] || 'http://localhost:8080';

const JOB_TITLES = [
  'Software Engineer', 'Senior Software Engineer', 'Staff Engineer', 'Principal Engineer',
  'Engineering Manager', 'Director of Engineering', 'VP of Engineering', 'CTO',
  'Product Manager', 'Senior Product Manager', 'Director of Product', 'VP of Product',
  'Data Scientist', 'Senior Data Scientist', 'ML Engineer', 'Data Engineer',
  'DevOps Engineer', 'SRE', 'Platform Engineer', 'Cloud Architect',
  'UX Designer', 'Senior UX Designer', 'Design Lead', 'Head of Design',
  'Frontend Engineer', 'Backend Engineer', 'Full Stack Engineer', 'Mobile Engineer',
  'QA Engineer', 'Senior QA Engineer', 'Test Automation Lead',
  'Security Engineer', 'Application Security Lead', 'CISO',
  'Technical Writer', 'Developer Advocate', 'Solutions Architect',
  'Business Analyst', 'Scrum Master', 'Agile Coach',
  'HR Manager', 'People Operations Lead', 'Talent Acquisition',
  'Marketing Manager', 'Content Strategist', 'Growth Engineer',
  'Sales Engineer', 'Account Executive', 'Customer Success Manager',
  'Finance Analyst', 'Operations Manager', 'Legal Counsel',
];

const DEPARTMENTS = [
  'Engineering', 'Engineering', 'Engineering', 'Engineering',
  'Product', 'Product',
  'Data Science', 'Data Science',
  'Infrastructure', 'Infrastructure',
  'Design', 'Design',
  'Quality Assurance',
  'Security',
  'Developer Experience',
  'Human Resources', 'People Ops',
  'Marketing', 'Growth',
  'Sales', 'Customer Success',
  'Finance', 'Operations', 'Legal',
];

const LOCATIONS = [
  'San Francisco, CA', 'San Francisco, CA', 'San Francisco, CA',
  'New York, NY', 'New York, NY',
  'Seattle, WA', 'Seattle, WA',
  'Austin, TX', 'Austin, TX',
  'Chicago, IL',
  'Boston, MA',
  'Denver, CO',
  'Portland, OR',
  'Los Angeles, CA',
  'Miami, FL',
  'Atlanta, GA',
  'London, UK',
  'Berlin, Germany',
  'Toronto, Canada',
  'Sydney, Australia',
  'Tokyo, Japan',
  'Singapore',
  'Dublin, Ireland',
  'Amsterdam, Netherlands',
  'Remote',  'Remote', 'Remote',
];

const TIMEZONES = [
  'America/Los_Angeles', 'America/New_York', 'America/Chicago',
  'America/Denver', 'America/Toronto', 'Europe/London',
  'Europe/Berlin', 'Europe/Amsterdam', 'Asia/Tokyo',
  'Asia/Singapore', 'Australia/Sydney', 'Europe/Dublin',
];

const PRONOUNS = [
  'he/him', 'he/him', 'he/him',
  'she/her', 'she/her', 'she/her',
  'they/them',
  null, null,
];

const SKILLS_POOL = [
  'JavaScript', 'TypeScript', 'React', 'Vue.js', 'Angular', 'Node.js', 'Python',
  'Java', 'Spring Boot', 'Go', 'Rust', 'C++', 'Swift', 'Kotlin',
  'PostgreSQL', 'MongoDB', 'Redis', 'Elasticsearch', 'GraphQL', 'REST APIs',
  'AWS', 'GCP', 'Azure', 'Kubernetes', 'Docker', 'Terraform',
  'Machine Learning', 'Deep Learning', 'NLP', 'Computer Vision', 'PyTorch', 'TensorFlow',
  'Figma', 'Sketch', 'User Research', 'Prototyping', 'Design Systems',
  'CI/CD', 'GitHub Actions', 'Jenkins', 'ArgoCD',
  'Microservices', 'Event-Driven Architecture', 'System Design', 'Distributed Systems',
  'Agile', 'Scrum', 'Kanban', 'OKRs',
  'Public Speaking', 'Technical Writing', 'Mentoring', 'Leadership',
  'Data Analysis', 'SQL', 'Spark', 'Airflow', 'dbt',
  'Security', 'Penetration Testing', 'OWASP', 'SOC2',
  'Product Strategy', 'A/B Testing', 'Analytics', 'Growth Hacking',
];

const INTERESTS_POOL = [
  'Photography', 'Hiking', 'Running', 'Cycling', 'Rock Climbing',
  'Coffee', 'Cooking', 'Baking', 'Wine Tasting', 'Craft Beer',
  'Reading', 'Science Fiction', 'Board Games', 'Video Games', 'Chess',
  'Music', 'Guitar', 'Piano', 'Jazz', 'Vinyl Records',
  'Travel', 'Backpacking', 'Scuba Diving', 'Surfing', 'Skiing',
  'Open Source', 'Hackathons', 'Tech Meetups', 'Blogging', 'Podcasts',
  'Yoga', 'Meditation', 'Fitness', 'CrossFit', 'Swimming',
  'Art', 'Painting', 'Woodworking', 'Ceramics', '3D Printing',
  'Gardening', 'Sustainability', 'Volunteering', 'Mentoring',
  'Film', 'Animation', 'Documentaries', 'Theater',
  'Dogs', 'Cats', 'Astronomy', 'History', 'Philosophy',
];

const BIOS = [
  'Passionate about building scalable systems and mentoring the next generation of engineers.',
  'Loves turning complex problems into simple, elegant solutions.',
  'Building the future of work, one feature at a time.',
  'Data-driven decision maker with a knack for storytelling.',
  'Believer in open source, continuous learning, and strong coffee.',
  'Bridging the gap between technology and human experience.',
  'Making the web faster, more accessible, and more delightful.',
  'Obsessed with developer experience and platform reliability.',
  'Bringing empathy and design thinking to everything I build.',
  'Always learning, always shipping, always improving.',
  'Helping teams work better together through great tools.',
  'Turning user insights into product magic.',
  'Security enthusiast. Privacy advocate. Bug hunter.',
  'Full stack human. Code by day, cook by night.',
  'Leading with curiosity and building with purpose.',
  'Remote work champion and async communication advocate.',
  'Making data science accessible to everyone in the org.',
  'Infrastructure nerd who dreams in YAML.',
  'Connecting people and ideas across the organization.',
  'Focused on growth, experimentation, and measurable impact.',
];

function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }
function pickN(arr, min, max) {
  const n = min + Math.floor(Math.random() * (max - min + 1));
  const shuffled = [...arr].sort(() => Math.random() - 0.5);
  return shuffled.slice(0, n);
}
function randomDate(startYear, endYear) {
  const start = new Date(startYear, 0, 1);
  const end = new Date(endYear, 11, 31);
  const d = new Date(start.getTime() + Math.random() * (end.getTime() - start.getTime()));
  return d.toISOString().split('T')[0];
}
function randomPhone() {
  const area = 200 + Math.floor(Math.random() * 800);
  const mid = 200 + Math.floor(Math.random() * 800);
  const end = 1000 + Math.floor(Math.random() * 9000);
  return `+1 ${area}-${mid}-${end}`;
}

async function main() {
  console.log('Fetching all users...');

  // Use a known admin user to fetch all users
  const res = await fetch(`${BASE}/api/users/search?q=`, {
    headers: { 'X-Debug-User-Id': '72057594037927937' },
  });
  const users = await res.json();
  console.log(`Found ${users.length} users\n`);

  // Collect all user IDs for manager assignment
  const userIds = users.map(u => u.id);

  let updated = 0;
  let skipped = 0;

  for (const user of users) {
    const jobTitle = pick(JOB_TITLES);
    const department = pick(DEPARTMENTS);
    const location = pick(LOCATIONS);
    const timezone = pick(TIMEZONES);
    const pronouns = pick(PRONOUNS);
    const skills = pickN(SKILLS_POOL, 3, 7).join(', ');
    const interests = pickN(INTERESTS_POOL, 2, 5).join(', ');
    const bio = pick(BIOS);
    const phone = Math.random() > 0.3 ? randomPhone() : null;
    const joinedCompanyAt = randomDate(2018, 2026);
    const linkedinUrl = Math.random() > 0.4 ? `https://linkedin.com/in/${user.username}` : null;

    // Assign a random manager (not self, and with some hierarchy)
    let managerId = null;
    if (Math.random() > 0.15) { // 85% have a manager
      const candidates = userIds.filter(id => String(id) !== String(user.id));
      if (candidates.length > 0) {
        managerId = String(candidates[Math.floor(Math.random() * candidates.length)]);
      }
    }

    const profile = {
      bio,
      jobTitle,
      department,
      location,
      timezone,
      skills,
      interests,
      joinedCompanyAt,
      managerId,
    };
    if (pronouns) profile.pronouns = pronouns;
    if (phone) profile.phone = phone;
    if (linkedinUrl) profile.linkedinUrl = linkedinUrl;

    try {
      const updateRes = await fetch(`${BASE}/api/users/me/profile`, {
        method: 'PUT',
        headers: {
          'Content-Type': 'application/json',
          'X-Debug-User-Id': String(user.id),
        },
        body: JSON.stringify(profile),
      });

      if (updateRes.ok) {
        updated++;
        if (updated % 20 === 0) {
          process.stdout.write(`  Updated ${updated}/${users.length} users\r`);
        }
      } else {
        skipped++;
        console.log(`  WARN: Failed to update ${user.displayName} (${updateRes.status})`);
      }
    } catch (e) {
      skipped++;
      console.log(`  ERROR: ${user.displayName}: ${e.message}`);
    }
  }

  console.log(`\nDone! Updated ${updated} users, skipped ${skipped}`);
  console.log('\nSample profiles:');

  // Show a few examples
  for (const uid of userIds.slice(0, 5)) {
    const r = await fetch(`${BASE}/api/users/${uid}`, {
      headers: { 'X-Debug-User-Id': '72057594037927937' },
    });
    const u = await r.json();
    console.log(`  ${u.displayName} — ${u.jobTitle} · ${u.department} · ${u.location}`);
    console.log(`    Skills: ${u.skills}`);
    console.log(`    Interests: ${u.interests}`);
    console.log('');
  }
}

main().catch(e => { console.error(e); process.exit(1); });
