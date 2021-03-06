package hudson.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;

import hudson.Extension;
import hudson.model.Descriptor.FormException;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;

/**
 * A configurable Radiator-Style job view suitable for use in extreme feedback
 * systems - ideal for running on a spare PC in the office. Many thanks to
 * Julien Renaut for the xfpanel plugin that inspired some of the updates to
 * this view.
 * 
 * @author Mark Howard (mh@tildemh.com)
 */
public class RadiatorView extends ListView
{

	private static final int DEFAULT_CAPTION_SIZE = 36;
	private static final int DEFAULT_FONT_SIZE = 16;

	private static final Logger LOGGER = Logger.getLogger(RadiatorView.class.getName());

	/**
	 * Cache of location of jobs in the build queue.
	 */
	transient Map<hudson.model.Queue.Item, Integer> placeInQueue = new HashMap<hudson.model.Queue.Item, Integer>();

	/**
	 * Colours to use in the view.
	 */
	transient ViewEntryColors colors;

	/**
	 * User configuration - show stable builds when there are some unstable builds.
	 */
	@DataBoundSetter
	Boolean showStable = false;

	/**
	 * User configuration - show details in stable builds.
	 */
	@DataBoundSetter
	Boolean showStableDetail = false;

	/**
	 * User configuration - show flexible rows.
	 */
	@DataBoundSetter
	Boolean flexibleRows = false;

	/**
	 * User configuration - font size in points (1pt = 1/72in) for the job's titles
	 * to be used on the radiator.
	 */
	@DataBoundSetter
	Integer fontSize;

	/**
	 * User configuration - show build stability icon.
	 */
	@DataBoundSetter
	Boolean showBuildStability = false;

	/**
	 * User configuration - high visibility mode.
	 */
	@DataBoundSetter
	Boolean highVis = true;

	/**
	 * User configuration - group builds by common prefix.
	 */
	@DataBoundSetter
	Boolean groupByPrefix = true;

	/**
	 * User configuration - text for the caption to be used on the radiator's
	 * headline.
	 */
	@DataBoundSetter
	String captionText;

	/**
	 * User configuration - size in points (1pt = 1/72in) for the caption to be used
	 * on the radiator's headline.
	 */
	@DataBoundSetter
	Integer captionSize;

	/**
	 * @param name view name.
	 */
	@DataBoundConstructor
	public RadiatorView(String name)
	{
		super(name);
	}

	public ProjectViewEntry getContents()
	{
		ProjectViewEntry content = new ProjectViewEntry();

		placeInQueue = new HashMap<hudson.model.Queue.Item, Integer>();
		int j = 1;
		for (hudson.model.Queue.Item i : Jenkins.get().getQueue().getItems())
		{
			placeInQueue.put(i, j++);
		}

		LOGGER.fine("Collecting items for view " + getViewName());
		addItems(getItems(), content);
		return content;
	}

	private void addItems(Collection<TopLevelItem> items, ProjectViewEntry content)
	{
		for (TopLevelItem item : items)
		{
			LOGGER.fine(item.getName() + " (" + item.getClass() + ")");
			if (item instanceof AbstractFolder)
			{
				addItems(((AbstractFolder) item).getItems(), content);
			}
			if (item instanceof Job)
			{
				IViewEntry entry = new JobViewEntry((Job<?, ?>) item);
				content.addBuild(entry);
			}
		}
	}

	public ProjectViewEntry getContentsByPrefix()
	{
		ProjectViewEntry contents = new ProjectViewEntry();
		ProjectViewEntry allContents = getContents();
		Map<String, ProjectViewEntry> jobsByPrefix = new HashMap<String, ProjectViewEntry>();

		for (IViewEntry job : allContents.getJobs())
		{
			String prefix = getPrefix(job.getName());
			ProjectViewEntry project = jobsByPrefix.get(prefix);
			if (project == null)
			{
				project = new ProjectViewEntry(prefix);
				jobsByPrefix.put(prefix, project);
				contents.addBuild(project);
			}
			project.addBuild(job);
		}
		return contents;
	}

	private String getPrefix(String name)
	{
		if (name.contains("_")) { return StringUtils.substringBefore(name, "_"); }
		if (name.contains("-")) { return StringUtils.substringBefore(name, "-"); }
		if (name.contains(":"))
		{
			return StringUtils.substringBefore(name, ":");
		} else return "No Project";
	}

	@Override
	protected void submit(StaplerRequest req) throws ServletException, IOException, FormException
	{
		super.submit(req);

		JSONObject json = req.getSubmittedForm();

		if (json.optBoolean("flexibleRows", json.has("fontSize")))
		{
			this.flexibleRows = true;
			fontSize = json.optInt("fontSize");
		} else
		{
			this.flexibleRows = false;
			fontSize = DEFAULT_FONT_SIZE;
		}

		this.showStable = Boolean.parseBoolean(req.getParameter("showStable"));
		this.showStableDetail = Boolean.parseBoolean(req.getParameter("showStableDetail"));
		this.highVis = Boolean.parseBoolean(req.getParameter("highVis"));
		this.groupByPrefix = Boolean.parseBoolean(req.getParameter("groupByPrefix"));
		this.showBuildStability = Boolean.parseBoolean(req.getParameter("showBuildStability"));
		this.captionText = req.getParameter("captionText");
		try
		{
			this.captionSize = Integer.parseInt(req.getParameter("captionSize"));
		} catch (NumberFormatException e)
		{
			this.captionSize = DEFAULT_CAPTION_SIZE;
		}
	}

	public Boolean getShowStable()
	{
		return showStable;
	}

	public Boolean getShowStableDetail()
	{
		return showStableDetail;
	}

	public Boolean getFlexibleRows()
	{
		return flexibleRows;
	}

	public Integer getFontSize()
	{
		return fontSize;
	}

	public Boolean getHighVis()
	{
		return highVis;
	}

	public Boolean getGroupByPrefix()
	{
		return groupByPrefix;
	}

	public Boolean getShowBuildStability()
	{
		return showBuildStability;
	}

	public String getCaptionText()
	{
		return captionText;
	}

	public Integer getCaptionSize()
	{
		return captionSize;
	}

	/**
	 * Converts a list of jobs to a list of list of jobs, suitable for display as
	 * rows in a table.
	 * 
	 * @param jobs        the jobs to include.
	 * @param failingJobs if this is a list of failing jobs, in which case fewer
	 *                    jobs should be used per row.
	 * @return a list of fixed size view entry lists.
	 */
	public Collection<Collection<IViewEntry>> toRows(Collection<IViewEntry> jobs, Boolean failingJobs)
	{
		int jobsPerRow = 1;
		if (failingJobs.booleanValue())
		{
			if (jobs.size() > 3)
			{
				jobsPerRow = 2;
			}
			if (jobs.size() > 9)
			{
				jobsPerRow = 3;
			}
			if (jobs.size() > 15)
			{
				jobsPerRow = 4;
			}
		} else
		{
			// don't mind having more rows as much for passing jobs.
			jobsPerRow = (int) Math.floor(Math.sqrt(jobs.size()) / 1.5);
		}
		Collection<Collection<IViewEntry>> rows = new ArrayList<Collection<IViewEntry>>();
		Collection<IViewEntry> current = null;
		int i = 0;
		for (IViewEntry job : jobs)
		{
			if (i == 0)
			{
				current = new ArrayList<IViewEntry>();
				rows.add(current);
			}
			current.add(job);
			i++;
			if (i >= jobsPerRow)
			{
				i = 0;
			}
		}
		return rows;
	}

	@Extension
	public static class DescriptorImpl extends ListView.DescriptorImpl
	{
		@Override
		public String getDisplayName()
		{
			return "Radiator";
		}
	}
}
