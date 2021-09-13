import React from 'react'
import { FormattedMessage } from 'react-intl'
import { useResource } from 'rest-hooks'

import { Button, MainPageWithScroll } from '@app/components'
import { Routes } from '../../../routes'
import PageTitle from '@app/components/PageTitle'
import useRouter from '@app/hooks/useRouter'
import DestinationsTable from './components/DestinationsTable'
import DestinationResource from '@app/core/resources/Destination'
import HeadTitle from '@app/components/HeadTitle'
import Placeholder, { ResourceTypes } from '@app/components/Placeholder'
import useWorkspace from '@app/hooks/services/useWorkspace'

const AllDestinationsPage: React.FC = () => {
  const { push } = useRouter()
  const { workspace } = useWorkspace()
  const { destinations } = useResource(DestinationResource.listShape(), {
    workspaceId: workspace.workspaceId,
  })

  const onCreateDestination = () =>
    push(`${Routes.Destination}${Routes.DestinationNew}`)

  return (
    <MainPageWithScroll
      headTitle={<HeadTitle titles={[{ id: 'admin.destinations' }]} />}
      pageTitle={
        <PageTitle
          title={<FormattedMessage id="admin.destinations" />}
          endComponent={
            <Button onClick={onCreateDestination} data-id="new-destination">
              <FormattedMessage id="destination.newDestination" />
            </Button>
          }
        />
      }
    >
      {destinations.length ? (
        <DestinationsTable destinations={destinations} />
      ) : (
        <Placeholder resource={ResourceTypes.Destinations} />
      )}
    </MainPageWithScroll>
  )
}

export default AllDestinationsPage
